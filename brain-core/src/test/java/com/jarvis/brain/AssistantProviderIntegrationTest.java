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
        System.out.println("AssistantProviderIntegrationTest: " + checks + " assertions passed");
    }

    private static void reasoningReceivesRealToolContract() {
        BrainEngine reflex = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC));
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
        BrainEngine reflex = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC));
        ToolRegistry tools = ToolRegistry.standard();
        ReasoningRouter router = request -> new ReasoningResult("bad-model", "Doing it.",
                new Plan("unsafe", List.of(new PlanStep("invented_system_control"))));
        AssistantCore core = new AssistantCore(reflex, router, tools);
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
        BrainEngine reflex = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC));
        AssistantCore core = new AssistantCore(reflex, policy, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse response = core.handle("figure out a hard problem for me");
        check(response.text().contains("Handled locally"), "assistant should accept cost-safe router as its cortex");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
