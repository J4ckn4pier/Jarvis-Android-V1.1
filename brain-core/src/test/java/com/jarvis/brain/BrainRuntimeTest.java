package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

public final class BrainRuntimeTest {
    private static int checks;
    public static void main(String[] args) {
        deviceReflexExecutesThroughSharedRegistry();
        consequentialPlanPausesAndResumesOnlyAfterApproval();
        openEndedRequestUsesReasoningRouterInsteadOfLegacyFailureText();
        partialExecutionFailureReportsCompletedWork();
        partialRecoveryReportsCompletedWork();
        sideRequestPartialFailureReportsCompletedWorkAndPreservesApproval();
        System.out.println("BrainRuntimeTest: " + checks + " assertions passed");
    }

    private static void deviceReflexExecutesThroughSharedRegistry() {
        int[] calls = {0};
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ToolSpec("open_dialer", false, Set.of("phone", "phone app"), Set.of(),
                "Open dialer", ToolExecutionClass.DEVICE_REFLEX), (a,c) -> { calls[0]++; return ToolResult.success("Dialer opened."); });
        BrainRuntime runtime = runtime(tools, req -> new ReasoningResult("fallback", "I can help with that.", null));
        runtime.handle("Hey Jarvis");
        BrainRuntime.Result result = runtime.handle("phone app");
        check(result.status() == BrainRuntime.Status.COMPLETED, "device reflex completes");
        check(calls[0] == 1, "device tool executes once");
        check(result.text().contains("Dialer opened"), "tool outcome reaches user-facing result");
    }

    private static void consequentialPlanPausesAndResumesOnlyAfterApproval() {
        int[] sends = {0};
        ToolRegistry tools = ToolRegistry.standard();
        tools.register(new ToolSpec("send_message", true, Set.of("text", "message"), Set.of("recipient","message"),
                "Send message", ToolExecutionClass.CONSEQUENTIAL), (a,c) -> { sends[0]++; return ToolResult.success("Message ready for " + a.get("recipient")); });
        BrainRuntime runtime = runtime(tools, req -> new ReasoningResult("planner", "I can prepare that.",
                new Plan("Send message", java.util.List.of(new PlanStep("send_message", Map.of("recipient","Mom","message","I'm late"), true)))));
        runtime.handle("Hey Jarvis");
        BrainRuntime.Result blocked = runtime.handle("tell Mom I'm late");
        check(blocked.status() == BrainRuntime.Status.APPROVAL_REQUIRED, "consequential action pauses");
        check(sends[0] == 0, "nothing sent before approval");
        check("send_message".equals(blocked.blockedTool()), "blocked tool identified");
        BrainRuntime.Result approved = runtime.approvePending();
        check(approved.status() == BrainRuntime.Status.COMPLETED, "approved action resumes");
        check(sends[0] == 1, "single-use approval executes exactly once");
    }

    private static void openEndedRequestUsesReasoningRouterInsteadOfLegacyFailureText() {
        ToolRegistry tools = ToolRegistry.standard();
        BrainRuntime runtime = runtime(tools, req -> new ReasoningResult("local-cortex", "I’d start by comparing the constraints and options.", null));
        runtime.handle("Hey Jarvis");
        BrainRuntime.Result result = runtime.handle("help me think through whether I should move next month");
        check(result.status() == BrainRuntime.Status.COMPLETED, "conversation completes");
        check(result.text().contains("comparing the constraints"), "reasoning answer used");
        check(!result.text().toLowerCase().contains("reliable interpretation"), "legacy failure language gone");
        check(!result.text().toLowerCase().contains("no framework"), "no framework failure gone");
    }

    private static void partialExecutionFailureReportsCompletedWork() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ToolSpec("first_step", false, Set.of(), Set.of(),
                "Complete first step", ToolExecutionClass.DEVICE_REFLEX),
                (a,c) -> ToolResult.success("First step completed."));
        tools.register(new ToolSpec("second_step", false, Set.of(), Set.of(),
                "Attempt second step", ToolExecutionClass.DEVICE_REFLEX),
                (a,c) -> ToolResult.failure("Second step failed."));
        Plan plan = new Plan("two-stage operation", java.util.List.of(
                new PlanStep("first_step", Map.of(), false),
                new PlanStep("second_step", Map.of(), false)));
        BrainRuntime runtime = runtime(tools, req -> new ReasoningResult("planner", "I’ll handle both steps.", plan));
        runtime.handle("Hey Jarvis");
        BrainRuntime.Result result = runtime.handle("perform the two-stage operation");
        check(result.status() == BrainRuntime.Status.FAILED, "later-step failure remains a failure");
        check(result.outputs().contains("First step completed."), "execution report retains earlier completed work");
        check(result.text().contains("First step completed."),
                "user-facing failure must disclose work that already completed before the failure");
        check(result.text().contains("Second step failed."),
                "user-facing failure must still disclose the step that failed");
    }

    private static void partialRecoveryReportsCompletedWork() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ToolSpec("first_step", false, Set.of(), Set.of(),
                "Complete first step", ToolExecutionClass.DEVICE_REFLEX),
                (a,c) -> ToolResult.success("First step completed."));
        tools.register(new ToolSpec("flaky_step", false, Set.of(), Set.of(),
                "Attempt flaky step", ToolExecutionClass.DEVICE_REFLEX),
                (a,c) -> ToolResult.retryableFailure("Second step is temporarily unavailable."));
        Plan plan = new Plan("two-stage recoverable operation", java.util.List.of(
                new PlanStep("first_step", Map.of(), false),
                new PlanStep("flaky_step", Map.of(), false)));
        BrainRuntime runtime = runtime(tools, req -> new ReasoningResult("planner", "I’ll handle both steps.", plan));
        runtime.handle("Hey Jarvis");
        BrainRuntime.Result result = runtime.handle("perform the recoverable operation");
        check(result.status() == BrainRuntime.Status.RECOVERY_REQUIRED, "repeated temporary failure asks for recovery");
        check(result.outputs().contains("First step completed."), "recovery report retains earlier completed work");
        check(result.text().contains("First step completed."),
                "user-facing recovery must disclose work that already completed before recovery was needed");
        check(result.text().contains("Second step is temporarily unavailable."),
                "user-facing recovery must disclose why the remaining step paused");
    }

    private static void sideRequestPartialFailureReportsCompletedWorkAndPreservesApproval() {
        int[] sends = {0};
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ToolSpec("send_message", true, Set.of(), Set.of("recipient","message"),
                "Send message", ToolExecutionClass.CONSEQUENTIAL),
                (a,c) -> { sends[0]++; return ToolResult.success("Message sent."); });
        tools.register(new ToolSpec("side_first", false, Set.of(), Set.of(),
                "Complete safe side step", ToolExecutionClass.DEVICE_REFLEX),
                (a,c) -> ToolResult.success("Side step one completed."));
        tools.register(new ToolSpec("side_second", false, Set.of(), Set.of(),
                "Fail safe side step", ToolExecutionClass.DEVICE_REFLEX),
                (a,c) -> ToolResult.failure("Side step two failed."));
        ReasoningRouter reasoner = req -> {
            if (req.utterance().toLowerCase().contains("side operation")) {
                return new ReasoningResult("planner", "I’ll handle the side operation.", new Plan("safe side operation",
                        java.util.List.of(new PlanStep("side_first", Map.of(), false), new PlanStep("side_second", Map.of(), false))));
            }
            return new ReasoningResult("planner", "I can prepare the original action.", new Plan("original action",
                    java.util.List.of(new PlanStep("send_message", Map.of("recipient","Mom","message","On my way"), true))));
        };
        BrainRuntime runtime = runtime(tools, reasoner);
        runtime.handle("Hey Jarvis");
        BrainRuntime.Result pending = runtime.handle("perform the original operation");
        check(pending.status() == BrainRuntime.Status.APPROVAL_REQUIRED, "original action waits for approval");
        BrainRuntime.Result side = runtime.handle("perform the side operation");
        check(side.status() == BrainRuntime.Status.APPROVAL_REQUIRED, "side failure preserves original approval status");
        check(runtime.hasPendingApproval(), "original approval remains pending after safe side failure");
        check(sends[0] == 0, "safe side failure never executes original consequential action");
        check(side.outputs().contains("Side step one completed."), "side execution report retains completed side work");
        check(side.text().contains("Side step one completed."),
                "user-facing side failure must disclose side work that already completed");
        check(side.text().contains("Side step two failed."),
                "user-facing side failure must disclose the failed side step");
    }

    private static BrainRuntime runtime(ToolRegistry tools, ReasoningRouter reasoner) {
        BrainEngine engine = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T20:10:00Z"), ZoneOffset.UTC));
        return new BrainRuntime(new AssistantCore(engine, reasoner, tools), tools);
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
