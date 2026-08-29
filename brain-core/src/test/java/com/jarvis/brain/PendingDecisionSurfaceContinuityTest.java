package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Side-question answers must not make approval/recovery affordances disappear from the surface projection. */
public final class PendingDecisionSurfaceContinuityTest {
    private static int checks;

    public static void main(String[] args) {
        approvalSurfaceSurvivesSideAnswer();
        recoverySurfaceSurvivesSideAnswer();
        System.out.println("PendingDecisionSurfaceContinuityTest: " + checks + " assertions passed");
    }

    private static void approvalSurfaceSurvivesSideAnswer() {
        ToolRegistry tools = registry(false);
        RuntimeApprovalConversation conversation = bridge(tools);
        RuntimeSurfacePresentation pending = conversation.handle("Jarvis, send pending message");
        check(pending.state() == AssistantSurfaceState.AWAITING_APPROVAL, "approval starts pending");

        RuntimeSurfacePresentation side = conversation.handle("what is the diagnostic value?");
        check(side.text().contains("72"), "side answer remains visible");
        check(side.state() == AssistantSurfaceState.AWAITING_APPROVAL, "surface still exposes approval state after side answer");
        check(side.primaryAction() == RuntimeSurfaceAction.APPROVE, "approve affordance remains visible");
        check(side.secondaryAction() == RuntimeSurfaceAction.CANCEL, "cancel affordance remains visible");
        check(conversation.hasPendingApproval(), "approval remains pending underneath surface");
    }

    private static void recoverySurfaceSurvivesSideAnswer() {
        ToolRegistry tools = registry(true);
        RuntimeApprovalConversation conversation = bridge(tools);
        RuntimeSurfacePresentation pending = conversation.handle("Jarvis, perform recoverable operation");
        check(pending.state() == AssistantSurfaceState.NEEDS_INPUT, "recovery starts pending");

        RuntimeSurfacePresentation side = conversation.handle("what is the diagnostic value?");
        check(side.text().contains("72"), "recovery side answer remains visible");
        check(side.state() == AssistantSurfaceState.NEEDS_INPUT, "surface still exposes recovery state after side answer");
        check(side.primaryAction() == RuntimeSurfaceAction.RETRY, "retry affordance remains visible");
        check(side.secondaryAction() == RuntimeSurfaceAction.CANCEL, "recovery cancel affordance remains visible");
        check(conversation.hasPendingRecovery(), "recovery remains pending underneath surface");
    }

    private static ToolRegistry registry(boolean recovery) {
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("send_message", true, Set.of("send"), Set.of(),
                "send message", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> ToolResult.success("sent"));
        tools.register(new ToolSpec("recoverable_action", false, Set.of("recoverable"), Set.of(),
                "recoverable operation", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) ->
                recovery ? ToolResult.retryableFailure("temporary failure") : ToolResult.success("ok"));
        tools.register(new ToolSpec("read_info", false, Set.of("diagnostic"), Set.of(),
                "read diagnostic information", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) ->
                ToolResult.success("72 diagnostic units"));
        return tools;
    }

    private static RuntimeApprovalConversation bridge(ToolRegistry tools) {
        ReasoningRouter reasoning = request -> {
            String u = request.utterance().toLowerCase();
            if (u.contains("pending message")) {
                return new ReasoningResult("test", "Ready to send.", new Plan("send",
                        List.of(new PlanStep("send_message", Map.of(), true))));
            }
            if (u.contains("recoverable operation")) {
                return new ReasoningResult("test", "Starting.", new Plan("recover",
                        List.of(new PlanStep("recoverable_action", Map.of(), false))));
            }
            if (u.contains("diagnostic value")) {
                return new ReasoningResult("test", "Checking.", new Plan("read",
                        List.of(new PlanStep("read_info", Map.of(), false))));
            }
            return new ReasoningResult("test", "reasoned", null);
        };
        BrainEngine engine = BrainEngine.createDefault(
                Clock.fixed(Instant.parse("2026-08-29T20:20:00Z"), ZoneOffset.UTC));
        return new RuntimeApprovalConversation(new BrainRuntime(new AssistantCore(engine, reasoning, tools), tools));
    }

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
