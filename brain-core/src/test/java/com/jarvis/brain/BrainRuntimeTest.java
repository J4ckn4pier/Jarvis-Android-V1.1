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

    private static BrainRuntime runtime(ToolRegistry tools, ReasoningRouter reasoner) {
        BrainEngine engine = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T20:10:00Z"), ZoneOffset.UTC));
        return new BrainRuntime(new AssistantCore(engine, reasoner, tools), tools);
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
