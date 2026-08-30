package com.jarvis.brain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PendingClarificationTest {
    private static int checks;

    public static void main(String[] args) {
        missingDestinationBecomesNaturalFollowupAndResumesPlan();
        consequentialFlagSurvivesClarificationResume();
        approvalAndClarificationRemainDistinctResumeCapabilities();
        harmlessResearchContinuesAfterClarification();
        clarificationResumePreservesWakeAcceptanceProvenance();
        explicitWakeCommandInterruptsPendingClarification();
        expiredSessionCannotResumeClarificationWithoutWake();
        System.out.println("PendingClarificationTest: " + checks + " assertions passed");
    }

    private static void missingDestinationBecomesNaturalFollowupAndResumesPlan() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        final int[] providerCalls = {0};
        ReasoningRouter router = request -> {
            providerCalls[0]++;
            return new ReasoningResult("planner", "I'll route you there.",
                    new Plan("Navigate", List.of(new PlanStep("navigate", Map.of(), false))));
        };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse first = core.handle("take me there");
        check(first.kind() == BrainResponse.Kind.CONVERSATION, "missing destination should ask, not expose invalid action plan");
        check(first.text().toLowerCase().contains("where") || first.text().toLowerCase().contains("destination"),
                "clarification should ask naturally for destination");

        BrainResponse resumed = core.handle("Castle Cafe");
        check(resumed.kind() == BrainResponse.Kind.ACTION_PLAN, "clarification answer should resume pending plan");
        check(resumed.plan().steps().get(0).arguments().get("destination").equals("Castle Cafe"),
                "answer should fill the single missing destination field");
        check(providerCalls[0] == 1, "simple clarification should not restart provider planning");
    }

    private static void consequentialFlagSurvivesClarificationResume() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        ToolRegistry tools = ToolRegistry.standard();
        ReasoningRouter router = request -> new ReasoningResult("planner", "I'll prepare the message.",
                new Plan("Message", List.of(new PlanStep("send_message", Map.of("message", "I'm running late"), false))));
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, tools);
        core.handle("Hey Jarvis");
        BrainResponse first = core.handle("tell someone I'm running late");
        check(first.kind() == BrainResponse.Kind.CONVERSATION, "missing recipient should ask for clarification");
        BrainResponse resumed = core.handle("Mom");
        check(resumed.kind() == BrainResponse.Kind.ACTION_PLAN, "recipient answer should resume action plan");
        check(resumed.plan().steps().get(0).consequential(),
                "registry-enforced consequential flag must survive clarification fill");
        check(resumed.plan().requiresApproval(), "resumed outbound message must still require approval");
    }

    private static void approvalAndClarificationRemainDistinctResumeCapabilities() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        ToolRegistry tools = ToolRegistry.standard();
        ReasoningRouter router = request -> new ReasoningResult("planner", "I'll prepare the message.",
                new Plan("Message", List.of(new PlanStep("send_message", Map.of("message", "I'm running late"), false))));

        ApprovalGate unrelatedApproval = new ApprovalGate();
        unrelatedApproval.approve("send_message");
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, tools);
        core.handle("Hey Jarvis");
        BrainResponse needsClarification = core.handle("tell someone I'm running late");
        check(needsClarification.kind() == BrainResponse.Kind.CONVERSATION,
                "approval capability must not satisfy a pending clarification");

        BrainResponse clarified = core.handle("Mom");
        check(clarified.kind() == BrainResponse.Kind.ACTION_PLAN && clarified.plan().requiresApproval(),
                "clarification answer must preserve the separate approval boundary");
        ApprovalGate executionApproval = new ApprovalGate();
        ResumablePlanExecutor executor = new ResumablePlanExecutor(tools, executionApproval);
        ExecutionCursor cursor = executor.start(clarified.plan());
        ExecutionReport blocked = executor.run(cursor, new ExecutionContext());
        check(blocked.status() == ExecutionReport.Status.APPROVAL_REQUIRED,
                "clarification answer must not be usable as approval for consequential execution");
    }

    private static void harmlessResearchContinuesAfterClarification() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        ToolRegistry tools = new ToolRegistry();
        int[] researchCalls = {0};
        tools.register(new ToolSpec("research_topic", false, Set.of(), Set.of("topic"), "Research a topic",
                        ToolExecutionClass.AUTONOMOUS_RESEARCH),
                (arguments, context) -> {
                    researchCalls[0]++;
                    return ToolResult.success("evidence for " + arguments.get("topic"));
                });
        int[] providerCalls = {0};
        ReasoningRouter router = request -> {
            providerCalls[0]++;
            if (providerCalls[0] == 1) {
                return new ReasoningResult("planner", "What should I research?",
                        new Plan("Research", List.of(new PlanStep("research_topic", Map.of(), false))));
            }
            check(request.utterance().equals("investigate something for me"),
                    "research synthesis after clarification must retain the user's original goal");
            check(request.context().contains("evidence for orbital mechanics"),
                    "research observation should be returned to reasoning after clarification");
            return new ReasoningResult("planner", "I found the answer.", null);
        };

        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, tools);
        core.handle("Hey Jarvis");
        BrainResponse first = core.handle("investigate something for me");
        check(first.kind() == BrainResponse.Kind.CONVERSATION,
                "missing harmless research input should ask for clarification");

        BrainResponse resumed = core.handle("orbital mechanics");
        check(resumed.kind() == BrainResponse.Kind.CONVERSATION,
                "completed harmless research should continue automatically after clarification");
        check(researchCalls[0] == 1,
                "harmless research should execute once after the missing input is supplied");
        check(providerCalls[0] == 2,
                "clarification should continue the bounded research loop without replanning before execution");
    }

    private static void clarificationResumePreservesWakeAcceptanceProvenance() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        ReasoningRouter router = request -> new ReasoningResult("planner", "Where should I send you?",
                new Plan("Navigate", List.of(new PlanStep("navigate", Map.of(), false))));
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());

        BrainResponse first = core.handle("Jarvis take me somewhere");
        check(first.kind() == BrainResponse.Kind.CONVERSATION, "explicit-wake request with a missing argument should ask for clarification");
        check(first.sessionActive(), "clarification should retain the active conversation session");
        check(!first.acceptedWithoutWakeWord(), "explicit wake provenance should be recorded on the clarification request");

        BrainResponse resumed = core.handle("Castle Cafe");
        check(resumed.sessionActive() == first.sessionActive(),
                "clarification resume must preserve the original session-active provenance");
        check(resumed.acceptedWithoutWakeWord() == first.acceptedWithoutWakeWord(),
                "clarification resume must not silently upgrade an explicit-wake request into a wake-free acceptance");
    }

    private static void explicitWakeCommandInterruptsPendingClarification() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        ReasoningRouter router = request -> new ReasoningResult("planner", "Where should I send you?",
                new Plan("Navigate", List.of(new PlanStep("navigate", Map.of(), false))));
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());

        BrainResponse first = core.handle("Jarvis take me somewhere");
        check(first.kind() == BrainResponse.Kind.CONVERSATION, "missing destination should establish a pending clarification");
        check(core.hasPendingPlan(), "navigation clarification should remain pending before interruption");

        BrainResponse interrupted = core.handle("Jarvis set a timer for 5 minutes");
        check(interrupted.kind() == BrainResponse.Kind.ACTION_PLAN,
                "a new explicit wake command should interrupt clarification instead of being consumed as its answer");
        check(interrupted.plan() != null && interrupted.plan().steps().get(0).tool().equals("set_timer"),
                "interruption should route the new command normally");
        check(interrupted.plan().steps().get(0).arguments().get("amount").equals("5"),
                "interruption must preserve the new command arguments");
        check(!core.hasPendingPlan(), "interrupted clarification should be discarded rather than silently resumed later");
    }

    private static void expiredSessionCannotResumeClarificationWithoutWake() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-08-27T23:30:00Z"));
        ReasoningRouter router = request -> new ReasoningResult("planner", "Where should I send you?",
                new Plan("Navigate", List.of(new PlanStep("navigate", Map.of(), false))));
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());

        BrainResponse first = core.handle("Jarvis take me somewhere");
        check(first.kind() == BrainResponse.Kind.CONVERSATION && core.hasPendingPlan(),
                "clarification should begin while the addressed conversation is active");
        clock.advance(Duration.ofMinutes(11));

        BrainResponse staleAnswer = core.handle("Castle Cafe");
        check(staleAnswer.kind() == BrainResponse.Kind.IGNORED_AMBIENT,
                "an expired listening session must not let a stale clarification accept wake-free speech");
        check(!core.hasPendingPlan(),
                "expired clarification capability should be discarded instead of surviving beyond the wake window");
    }

    private static final class AdjustableClock extends Clock {
        private Instant now;
        private AdjustableClock(Instant now) { this.now = now; }
        private void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
