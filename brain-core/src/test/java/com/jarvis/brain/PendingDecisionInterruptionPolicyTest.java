package com.jarvis.brain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pending runtime decisions must map new planned work onto the explicit ASK/DO_BOTH interruption vocabulary. */
public final class PendingDecisionInterruptionPolicyTest {
    private static int checks;

    public static void main(String[] args) {
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("safe_read", false, Set.of("read"), Set.of(),
                "safe read", ToolExecutionClass.DEVICE_REFLEX), (a, c) -> ToolResult.success("ok"));
        tools.register(new ToolSpec("send_message", true, Set.of("send"), Set.of("recipient", "message"),
                "send message", ToolExecutionClass.CONSEQUENTIAL), (a, c) -> ToolResult.success("sent"));
        tools.register(new ToolSpec("parking_search", false, Set.of("parking"), Set.of(),
                "parking search", ToolExecutionClass.DEVICE_REFLEX), (a, c) -> ToolResult.success("parking"));

        PendingDecisionInterruptionPolicy policy = new PendingDecisionInterruptionPolicy(tools);
        Plan safe = new Plan("read something", List.of(new PlanStep("safe_read", Map.of(), false)));
        Plan consequential = new Plan("send another message", List.of(new PlanStep("send_message",
                Map.of("recipient", "Mom", "message", "hi"), true)));
        Plan sameRecoveryTool = new Plan("search parking again", List.of(new PlanStep("parking_search", Map.of(), false)));

        check(policy.decide(BrainRuntime.Status.APPROVAL_REQUIRED, "send_message", safe) == InterruptionDecision.DO_BOTH,
                "safe independent work is DO_BOTH while approval waits");
        check(policy.decide(BrainRuntime.Status.APPROVAL_REQUIRED, "send_message", consequential) == InterruptionDecision.ASK,
                "new consequential work is ASK while approval waits");
        check(policy.decide(BrainRuntime.Status.RECOVERY_REQUIRED, "parking_search", safe) == InterruptionDecision.DO_BOTH,
                "safe independent work is DO_BOTH while recovery waits");
        check(policy.decide(BrainRuntime.Status.RECOVERY_REQUIRED, "parking_search", sameRecoveryTool) == InterruptionDecision.ASK,
                "same failed recovery tool cannot be silently re-entered");

        System.out.println("PendingDecisionInterruptionPolicyTest: " + checks + " assertions passed");
    }

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
