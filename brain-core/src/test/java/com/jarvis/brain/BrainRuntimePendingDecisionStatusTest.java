package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pending decision state must remain authoritative even when a harmless side request succeeds. */
public final class BrainRuntimePendingDecisionStatusTest {
    private static int checks;

    public static void main(String[] args) {
        safeSideRequestKeepsApprovalStatusAtRuntimeBoundary();
        System.out.println("BrainRuntimePendingDecisionStatusTest: " + checks + " assertions passed");
    }

    private static void safeSideRequestKeepsApprovalStatusAtRuntimeBoundary() {
        int[] sends = {0};
        int[] reads = {0};
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("send_message", true, Set.of(), Set.of("recipient", "message"),
                "send", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> {
                    sends[0]++;
                    return ToolResult.success("Message sent");
                });
        tools.register(new ToolSpec("read_info", false, Set.of(), Set.of(),
                "read info", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> {
                    reads[0]++;
                    return ToolResult.success("72 diagnostic units");
                });

        ReasoningRouter reasoning = request -> {
            if (request.utterance().toLowerCase().contains("diagnostic value")) {
                return new ReasoningResult("test", "Checking.", new Plan(
                        "answer safe side question", List.of(new PlanStep("read_info", Map.of(), false))));
            }
            return new ReasoningResult("test", "reasoned", null);
        };
        BrainEngine engine = BrainEngine.createDefault(
                Clock.fixed(Instant.parse("2026-08-30T16:05:00Z"), ZoneOffset.UTC));
        BrainRuntime runtime = new BrainRuntime(new AssistantCore(engine, reasoning, tools), tools);

        BrainRuntime.Result pending = runtime.handle("Jarvis, text Mom I am on my way");
        check(pending.status() == BrainRuntime.Status.APPROVAL_REQUIRED,
                "outbound message starts pending approval");

        BrainRuntime.Result side = runtime.handle("what is the diagnostic value?");
        check(reads[0] == 1, "harmless side request executes exactly once");
        check(sends[0] == 0, "side request never executes the pending consequential action");
        check(runtime.hasPendingApproval(), "original approval remains pending internally");
        check(side.text().contains("72"), "side result remains visible to the user");
        check(side.status() == BrainRuntime.Status.APPROVAL_REQUIRED,
                "runtime result must keep pending approval authoritative after successful side work");
        check("send_message".equals(side.blockedTool()),
                "runtime result must keep the original blocked tool visible");
    }

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
