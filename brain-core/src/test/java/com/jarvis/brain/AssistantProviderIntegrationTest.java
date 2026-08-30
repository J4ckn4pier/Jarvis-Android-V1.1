package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class AssistantProviderIntegrationTest {
    private static int checks;

    public static void main(String[] args) {
        reasoningReceivesRealToolContract();
        invalidProviderPlanIsNeverExposedAsActionPlan();
        policyRouterCanDriveAssistantWithoutPaidFallback();
        unexpectedReasoningFailureBecomesTruthfulConversation();
        nullReasoningResultBecomesTruthfulConversation();
        emptyReasoningResultBecomesTruthfulConversation();
        System.out.println("AssistantProviderIntegrationTest: " + checks + " assertions passed");
    }

    private static BrainEngine fixedBrain() {
        return BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC));
    }

    private static void reasoningReceivesRealToolContract() {
        BrainEngine reflex = fixedBrain();
        ToolRegistry tools = ToolRegistry.standard();
        final ReasoningRequest[] seen = {null};
        ReasoningRouter router = request -> {
            seen[0] = request;
            return new ReasoningResult("capture", "I'll reason about it.", null);
        };
        AssistantCore core = new AssistantCore(reflex, router, tools);
        core.handle("Hey Jarvis");
        core.handle("help me organize a complicated trip across town");
        check(seen[0] != null, "reasoning request should reach router");
        check(!seen[0].tools().isEmpty(), "reasoning provider must receive registered tool contracts");
        check(seen[0].tools().stream().anyMatch(t -> t.name().equals("send_message") && t.consequential()),
                "provider should know message tool is consequential");
        check(seen[0].tools().stream().anyMatch(t -> t.name().equals("navigate") && t.requiredArguments().contains("destination")),
                "provider should receive typed required arguments");
    }

    private static void invalidProviderPlanIsNeverExposedAsActionPlan() {
        ToolRegistry tools = ToolRegistry.standard();
        ReasoningRouter router = request -> new ReasoningResult("bad-model", "Doing it.",
                new Plan("unsafe", List.of(new PlanStep("invented_system_control"))));
        AssistantCore core = new AssistantCore(fixedBrain(), router, tools);
        core.handle("Hey Jarvis");
        BrainResponse response = core.handle("do something complicated for me");
        check(response.kind() != BrainResponse.Kind.ACTION_PLAN,
                "unvalidated provider plans must never cross the executive boundary as executable plans");
        check(response.text().toLowerCase().contains("clarif") || response.text().toLowerCase().contains("safe"),
                "invalid plan should become a safe clarification/recovery response");
    }

    private static void policyRouterCanDriveAssistantWithoutPaidFallback() {
        ReasoningProvider paid = new ReasoningProvider() {
            int calls;
            public String id() { return "paid"; }
            public boolean available() { return true; }
            public ReasoningResult reason(ReasoningRequest request) { calls++; return new ReasoningResult("paid", "paid", null); }
        };
        ReasoningProvider free = new ReasoningProvider() {
            public String id() { return "free"; }
            public boolean available() { return true; }
            public ReasoningResult reason(ReasoningRequest request) { return new ReasoningResult("free", "Handled locally.", null); }
        };
        PolicyProviderRouter policy = new PolicyProviderRouter(List.of(
                new ProviderRoute(paid, ProviderTier.PAID_EXTERNAL, 1),
                new ProviderRoute(free, ProviderTier.FREE_LOCAL, 1)
        ), false, 2);
        AssistantCore core = new AssistantCore(fixedBrain(), policy, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse response = core.handle("figure out a hard problem for me");
        check(response.text().contains("Handled locally"), "assistant should accept cost-safe router as its cortex");
    }

    private static void unexpectedReasoningFailureBecomesTruthfulConversation() {
        ReasoningRouter failing = request -> { throw new IllegalStateException("provider transport exploded"); };
        AssistantCore core = new AssistantCore(fixedBrain(), failing, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse response;
        try {
            response = core.handle("help me reason through a complicated decision");
        } catch (RuntimeException failure) {
            throw new AssertionError("provider/runtime failure must not escape the shared AssistantCore boundary", failure);
        }
        assertTruthfulReasoningFailure(response, "thrown provider failure");
    }

    private static void nullReasoningResultBecomesTruthfulConversation() {
        ReasoningRouter malformed = request -> null;
        AssistantCore core = new AssistantCore(fixedBrain(), malformed, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse response;
        try {
            response = core.handle("help me reason through another complicated decision");
        } catch (RuntimeException failure) {
            throw new AssertionError("null provider result must not escape the shared AssistantCore boundary", failure);
        }
        assertTruthfulReasoningFailure(response, "null provider result");
    }

    private static void emptyReasoningResultBecomesTruthfulConversation() {
        ReasoningRouter malformed = request -> new ReasoningResult("custom", null, null);
        AssistantCore core = new AssistantCore(fixedBrain(), malformed, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse response = core.handle("reason through a complicated problem with me");
        assertTruthfulReasoningFailure(response, "empty provider result");
    }

    private static void assertTruthfulReasoningFailure(BrainResponse response, String source) {
        check(response.kind() == BrainResponse.Kind.CONVERSATION,
                source + " should become a non-action conversational failure response");
        check(response.text() != null && response.text().toLowerCase().contains("couldn't") && response.text().toLowerCase().contains("safely"),
                source + " must be reported truthfully without inventing an answer");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}