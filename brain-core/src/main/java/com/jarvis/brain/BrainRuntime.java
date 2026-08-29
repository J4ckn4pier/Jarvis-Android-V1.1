package com.jarvis.brain;

import java.util.List;

/** Single execution facade between conversational/executive reasoning and platform tools. */
public final class BrainRuntime {
    public enum Status { COMPLETED, APPROVAL_REQUIRED, RECOVERY_REQUIRED, FAILED, IGNORED }
    public record Result(Status status, String text, String blockedTool, List<String> outputs) {
        public Result { if(status==null)throw new IllegalArgumentException("status required"); text=text==null?"":text.trim(); blockedTool=blockedTool==null?"":blockedTool.trim(); outputs=outputs==null?List.of():List.copyOf(outputs); }
    }
    private final AssistantCore assistant; private final ToolRegistry tools; private final ApprovalGate approvals=new ApprovalGate(); private final ResumablePlanExecutor executor; private ExecutionCursor pending; private String pendingTool=""; private Status pendingStatus;
    public BrainRuntime(AssistantCore assistant,ToolRegistry tools){if(assistant==null)throw new IllegalArgumentException("assistant required");if(tools==null)throw new IllegalArgumentException("tool registry required");this.assistant=assistant;this.tools=tools;this.executor=new ResumablePlanExecutor(tools,approvals);}
    public synchronized Result handle(String utterance){
        if(pending!=null&&pendingStatus==Status.RECOVERY_REQUIRED){return new Result(Status.RECOVERY_REQUIRED,"I still need your decision before I retry that action.",pendingTool,List.of());}
        if(pending!=null&&pendingStatus==Status.APPROVAL_REQUIRED)return handleSideQuestionWhileApprovalPending(utterance);
        BrainResponse response=assistant.handle(utterance);if(response.kind()==BrainResponse.Kind.IGNORED_AMBIENT)return new Result(Status.IGNORED,"","",List.of());if(response.kind()!=BrainResponse.Kind.ACTION_PLAN||response.plan()==null)return new Result(Status.COMPLETED,response.text(),"",List.of());pending=executor.start(response.plan());pendingStatus=null;return runPending(response.text());
    }
    public synchronized Result approvePending(){if(pending==null||pendingTool.isBlank()||pendingStatus!=Status.APPROVAL_REQUIRED)return new Result(Status.FAILED,"There is no action waiting for approval.","",List.of());approvals.approve(pendingTool);return runPending("");}
    public synchronized Result retryPending(){if(pending==null||pendingTool.isBlank()||pendingStatus!=Status.RECOVERY_REQUIRED)return new Result(Status.FAILED,"There is no action waiting for a recovery decision.","",List.of());pendingStatus=null;return runPending("");}
    public synchronized void cancelPending(){pending=null;pendingTool="";pendingStatus=null;}
    public synchronized boolean hasPendingApproval(){return pending!=null&&!pendingTool.isBlank()&&pendingStatus==Status.APPROVAL_REQUIRED;}
    public synchronized boolean hasPendingRecovery(){return pending!=null&&!pendingTool.isBlank()&&pendingStatus==Status.RECOVERY_REQUIRED;}
    private Result handleSideQuestionWhileApprovalPending(String utterance){
        BrainResponse response=assistant.handle(utterance);
        if(response.kind()==BrainResponse.Kind.IGNORED_AMBIENT)return new Result(Status.IGNORED,"","",List.of());
        if(response.kind()!=BrainResponse.Kind.ACTION_PLAN||response.plan()==null)return new Result(Status.COMPLETED,response.text(),"",List.of());
        if(!isSafeSidePlan(response.plan()))return new Result(Status.APPROVAL_REQUIRED,"I still need your decision on the pending action before I start another consequential action.",pendingTool,List.of());
        ExecutionReport report=executor.run(executor.start(response.plan()),new ExecutionContext());
        return switch(report.status()){
            case COMPLETED->new Result(Status.COMPLETED,lastNonBlank(report.outputs(),response.text()),"",report.outputs());
            case FAILED->new Result(Status.FAILED,report.failureDetail().isBlank()?"That side request failed safely.":report.failureDetail(),report.blockedTool(),report.outputs());
            case RECOVERY_REQUIRED->new Result(Status.FAILED,report.failureDetail().isBlank()?"That side request needs recovery before it can continue.":report.failureDetail(),report.blockedTool(),report.outputs());
            case APPROVAL_REQUIRED->new Result(Status.APPROVAL_REQUIRED,"I still need your decision on the original pending action before I start another consequential action.",pendingTool,report.outputs());
        };
    }
    private boolean isSafeSidePlan(Plan plan){
        for(PlanStep step:plan.steps()){
            if(step.consequential())return false;
            ToolRegistry.RegisteredTool tool=tools.resolve(step.tool()).orElse(null);
            if(tool==null||tool.spec().consequential()||tool.spec().executionClass()==ToolExecutionClass.CONSEQUENTIAL)return false;
        }
        return true;
    }
    private Result runPending(String assistantText){ExecutionReport report=executor.run(pending,new ExecutionContext());return switch(report.status()){
        case COMPLETED->{String text=lastNonBlank(report.outputs(),assistantText);clearPending();yield new Result(Status.COMPLETED,text,"",report.outputs());}
        case APPROVAL_REQUIRED->{pendingTool=report.blockedTool();pendingStatus=Status.APPROVAL_REQUIRED;String text=assistantText==null||assistantText.isBlank()?"I need your approval before I do that.":assistantText;yield new Result(Status.APPROVAL_REQUIRED,text,pendingTool,report.outputs());}
        case RECOVERY_REQUIRED->{pendingTool=report.blockedTool();pendingStatus=Status.RECOVERY_REQUIRED;yield new Result(Status.RECOVERY_REQUIRED,report.failureDetail().isBlank()?"That action needs recovery before I retry it.":report.failureDetail(),pendingTool,report.outputs());}
        case FAILED->{String detail=report.failureDetail().isBlank()?"That action failed safely.":report.failureDetail();clearPending();yield new Result(Status.FAILED,detail,report.blockedTool(),report.outputs());}
    };}
    private void clearPending(){pending=null;pendingTool="";pendingStatus=null;}
    private static String lastNonBlank(List<String> outputs,String fallback){if(outputs!=null)for(int i=outputs.size()-1;i>=0;i--){String v=outputs.get(i);if(v!=null&&!v.isBlank())return v.trim();}return fallback==null?"":fallback.trim();}
}
