package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Single execution facade between conversational/executive reasoning and platform tools. */
public final class BrainRuntime {
    public enum Status { COMPLETED, APPROVAL_REQUIRED, RECOVERY_REQUIRED, FAILED, IGNORED }
    public record Result(Status status, String text, String blockedTool, List<String> outputs) {
        public Result { if(status==null)throw new IllegalArgumentException("status required"); text=text==null?"":text.trim(); blockedTool=blockedTool==null?"":blockedTool.trim(); outputs=outputs==null?List.of():List.copyOf(outputs); }
    }
    private final AssistantCore assistant;
    private final ToolRegistry tools;
    private final ApprovalGate approvals=new ApprovalGate();
    private final ResumablePlanExecutor executor;
    private final PendingDecisionInterruptionPolicy pendingInterruptionPolicy;
    private final Clock clock;
    private final ActedOnEpisodeSink actedOnEpisodes;
    private ExecutionCursor pending;
    private Instant pendingRecommendedAt;
    private String pendingTool="";
    private Status pendingStatus;

    public BrainRuntime(AssistantCore assistant,ToolRegistry tools){
        this(assistant,tools,Clock.systemUTC(),ActedOnEpisodeSink.none());
    }

    public BrainRuntime(AssistantCore assistant,ToolRegistry tools,Clock clock,ActedOnEpisodeSink actedOnEpisodes){
        if(assistant==null)throw new IllegalArgumentException("assistant required");
        if(tools==null)throw new IllegalArgumentException("tool registry required");
        if(clock==null)throw new IllegalArgumentException("clock required");
        if(actedOnEpisodes==null)throw new IllegalArgumentException("acted-on episode sink required");
        this.assistant=assistant;
        this.tools=tools;
        this.executor=new ResumablePlanExecutor(tools,approvals);
        this.pendingInterruptionPolicy=new PendingDecisionInterruptionPolicy(tools);
        this.clock=clock;
        this.actedOnEpisodes=actedOnEpisodes;
    }
    public synchronized Result handle(String utterance){
        if(pending!=null&&(pendingStatus==Status.APPROVAL_REQUIRED||pendingStatus==Status.RECOVERY_REQUIRED))return handleSideQuestionWhileDecisionPending(utterance);
        BrainResponse response=assistant.handle(utterance);if(response.kind()==BrainResponse.Kind.IGNORED_AMBIENT)return new Result(Status.IGNORED,"","",List.of());if(response.kind()!=BrainResponse.Kind.ACTION_PLAN||response.plan()==null)return new Result(Status.COMPLETED,response.text(),"",List.of());pending=executor.start(response.plan());pendingRecommendedAt=clock.instant();pendingStatus=null;return runPending(response.text());
    }
    public synchronized Result approvePending(){if(pending==null||pendingTool.isBlank()||pendingStatus!=Status.APPROVAL_REQUIRED)return new Result(Status.FAILED,"There is no action waiting for approval.","",List.of());assistant.discardPendingClarification();approvals.approve(pendingTool);return runPending("");}
    public synchronized Result retryPending(){if(pending==null||pendingTool.isBlank()||pendingStatus!=Status.RECOVERY_REQUIRED)return new Result(Status.FAILED,"There is no action waiting for a recovery decision.","",List.of());assistant.discardPendingClarification();pendingStatus=null;return runPending("");}
    public synchronized void cancelPending(){clearPending();}
    public synchronized boolean hasPendingApproval(){return pending!=null&&!pendingTool.isBlank()&&pendingStatus==Status.APPROVAL_REQUIRED;}
    public synchronized boolean hasPendingRecovery(){return pending!=null&&!pendingTool.isBlank()&&pendingStatus==Status.RECOVERY_REQUIRED;}
    private Result handleSideQuestionWhileDecisionPending(String utterance){
        BrainResponse response=assistant.handle(utterance);
        if(response.kind()==BrainResponse.Kind.IGNORED_AMBIENT)return new Result(Status.IGNORED,"","",List.of());
        if(response.kind()!=BrainResponse.Kind.ACTION_PLAN||response.plan()==null)return new Result(Status.COMPLETED,response.text(),"",List.of());
        InterruptionDecision decision=pendingInterruptionPolicy.decide(pendingStatus,pendingTool,response.plan());
        if(decision!=InterruptionDecision.DO_BOTH)return pendingDecisionResult("I still need your decision on the pending action before I can run that request. I did not queue the new request; resolve the pending action first, then ask me again.",List.of());
        Instant recommendedAt=clock.instant();
        ExecutionReport report=executor.run(executor.start(response.plan()),new ExecutionContext());
        return switch(report.status()){
            case COMPLETED->{recordCompletedPlan(response.plan(),recommendedAt);yield new Result(Status.COMPLETED,lastNonBlank(report.outputs(),response.text()),"",report.outputs());}
            case FAILED->pendingDecisionResult(sideFailureText(report,"That side request failed safely."),report.outputs());
            case RECOVERY_REQUIRED->pendingDecisionResult(sideFailureText(report,"That side request needs recovery before it can continue."),report.outputs());
            case APPROVAL_REQUIRED->pendingDecisionResult("I still need your decision on the original pending action before I start another consequential action. I did not queue the new request; resolve the pending action first, then ask me again.",report.outputs());
        };
    }
    private Result pendingDecisionResult(String text,List<String> outputs){
        Status status=pendingStatus==Status.RECOVERY_REQUIRED?Status.RECOVERY_REQUIRED:Status.APPROVAL_REQUIRED;
        return new Result(status,text,pendingTool,outputs);
    }
    private static String sideFailureText(ExecutionReport report,String fallback){
        String detail=report.failureDetail()==null||report.failureDetail().isBlank()?fallback:report.failureDetail();
        return detail+" Your previous action is still waiting for your decision.";
    }
    private Result runPending(String assistantText){ExecutionReport report=executor.run(pending,new ExecutionContext());return switch(report.status()){
        case COMPLETED->{String text=lastNonBlank(report.outputs(),assistantText);recordCompletedPlan(pending.plan(),pendingRecommendedAt);clearPending();yield new Result(Status.COMPLETED,text,"",report.outputs());}
        case APPROVAL_REQUIRED->{pendingTool=report.blockedTool();pendingStatus=Status.APPROVAL_REQUIRED;String text=approvalText(report,assistantText);yield new Result(Status.APPROVAL_REQUIRED,text,pendingTool,report.outputs());}
        case RECOVERY_REQUIRED->{pendingTool=report.blockedTool();pendingStatus=Status.RECOVERY_REQUIRED;yield new Result(Status.RECOVERY_REQUIRED,report.failureDetail().isBlank()?"That action needs recovery before I retry it.":report.failureDetail(),pendingTool,report.outputs());}
        case FAILED->{String detail=report.failureDetail().isBlank()?"That action failed safely.":report.failureDetail();clearPending();yield new Result(Status.FAILED,detail,report.blockedTool(),report.outputs());}
    };}
    private static String approvalText(ExecutionReport report,String assistantText){
        if(assistantText!=null&&!assistantText.isBlank())return assistantText;
        String lastOutput=lastNonBlank(report.outputs(),"");
        if(!lastOutput.isBlank()&&report.failureDetail()!=null&&!report.failureDetail().isBlank()){
            return lastOutput+" I need fresh approval before I retry that action.";
        }
        return "I need your approval before I do that.";
    }
    private void recordCompletedPlan(Plan plan,Instant recommendedAt){
        if(plan==null||plan.steps()==null||plan.steps().isEmpty())return;
        Optional<PlanStep> actedStep=lastActedStep(plan);
        if(actedStep.isEmpty())return;
        Instant actedAt=clock.instant();
        String domain=actedStep.get().tool().trim();
        String subject=plan.goal()==null||plan.goal().isBlank()?domain:plan.goal().trim();
        RecommendationEpisode episode=new RecommendationEpisode(
                "runtime-"+UUID.randomUUID(),domain,subject,recommendedAt==null?actedAt:recommendedAt);
        try{actedOnEpisodes.recordActedOn(episode,actedAt);}catch(RuntimeException ignored){/* The action already happened; follow-up persistence must never rewrite execution truth. */}
    }
    private Optional<PlanStep> lastActedStep(Plan plan){
        List<PlanStep> steps=plan.steps();
        for(int i=steps.size()-1;i>=0;i--){
            PlanStep step=steps.get(i);
            if(step==null||step.tool()==null||step.tool().isBlank())continue;
            Optional<ToolRegistry.RegisteredTool> registered=tools.resolve(step.tool());
            if(registered.isPresent()&&registered.get().spec().executionClass()!=ToolExecutionClass.AUTONOMOUS_RESEARCH)return Optional.of(step);
        }
        return Optional.empty();
    }
    private void clearPending(){pending=null;pendingRecommendedAt=null;pendingTool="";pendingStatus=null;}
    private static String lastNonBlank(List<String> outputs,String fallback){if(outputs!=null)for(int i=outputs.size()-1;i>=0;i--){String v=outputs.get(i);if(v!=null&&!v.isBlank())return v.trim();}return fallback==null?"":fallback.trim();}
}
