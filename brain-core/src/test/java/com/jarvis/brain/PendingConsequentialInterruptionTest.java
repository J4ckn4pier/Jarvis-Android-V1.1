package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A second consequential request must never replace or silently queue behind an existing pending decision. */
public final class PendingConsequentialInterruptionTest {
    private static int checks;

    public static void main(String[] args) {
        approvalVariant();
        recoveryVariant();
        System.out.println("PendingConsequentialInterruptionTest: " + checks + " assertions passed");
    }

    private static void approvalVariant() {
        int[] originalSends = {0};
        int[] secondActions = {0};
        RuntimeApprovalConversation conversation = approvalBridge(originalSends, secondActions);

        RuntimeSurfacePresentation pending = conversation.handle("Jarvis, text Mom I am on my way");
        check(pending.state() == AssistantSurfaceState.AWAITING_APPROVAL, "approval variant begins pending");
        check(originalSends[0] == 0, "original consequential action has not executed before approval");

        RuntimeSurfacePresentation conflict = conversation.handle("also create the dinner event");
        check(conflict.state() == AssistantSurfaceState.AWAITING_APPROVAL, "second consequential request preserves original approval state");
        check(conflict.primaryAction() == RuntimeSurfaceAction.APPROVE, "original approve affordance remains visible");
        check(conflict.text().toLowerCase().contains("not queued"), "conflict text truthfully says second request was not queued");
        check(originalSends[0] == 0, "conflict does not execute original action early");
        check(secondActions[0] == 0, "conflict does not execute second consequential action");
        check(conversation.hasPendingApproval(), "original approval cursor remains pending after conflict");

        RuntimeSurfacePresentation done = conversation.handle("confirm", 0.95);
        check(done.state() == AssistantSurfaceState.ACTION_DONE, "explicit confirm still resumes original approval");
        check(originalSends[0] == 1, "only original consequential action executes after confirm");
        check(secondActions[0] == 0, "second consequential request was not hidden-queued for later execution");
    }

    private static void recoveryVariant() {
        int[] originalAttempts = {0};
        int[] secondActions = {0};
        RuntimeApprovalConversation conversation = recoveryBridge(originalAttempts, secondActions);

        RuntimeSurfacePresentation pending = conversation.handle("Jarvis, perform zeta operation");
        check(pending.state() == AssistantSurfaceState.NEEDS_INPUT, "recovery variant begins pending");
        check(originalAttempts[0] == 2, "original action reaches bounded recovery decision");

        RuntimeSurfacePresentation conflict = conversation.handle("also send the status message");
        check(conflict.state() == AssistantSurfaceState.NEEDS_INPUT, "second consequential request preserves original recovery state");
        check(conflict.primaryAction() == RuntimeSurfaceAction.RETRY, "original retry affordance remains visible");
        check(conflict.text().toLowerCase().contains("not queued"), "recovery conflict text truthfully says second request was not queued");
        check(originalAttempts[0] == 2, "conflict does not retry original action implicitly");
        check(secondActions[0] == 0, "conflict does not execute second consequential action");
        check(conversation.hasPendingRecovery(), "original recovery cursor remains pending after conflict");

        RuntimeSurfacePresentation done = conversation.handle("retry", 0.95);
        check(done.state() == AssistantSurfaceState.ACTION_DONE, "explicit retry still resumes original recovery");
        check(originalAttempts[0] == 3, "only original recovery action resumes once after retry");
        check(secondActions[0] == 0, "second consequential request remains unqueued after original recovery completes");
    }

    private static RuntimeApprovalConversation approvalBridge(int[] originalSends, int[] secondActions) {
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"),
                "send", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> {
                    originalSends[0]++;
                    return ToolResult.success("Message sent");
                });
        tools.register(new ToolSpec("create_event", true, Set.of("event", "calendar"), Set.of(),
                "create calendar event", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> {
                    secondActions[0]++;
                    return ToolResult.success("Event created");
                });

        ReasoningRouter reasoning = request -> {
            if (request.utterance().toLowerCase().contains("dinner event")) {
                return new ReasoningResult("test", "Creating event.", new Plan(
                        "create second consequential event",
                        List.of(new PlanStep("create_event", Map.of(), true))));
            }
            return new ReasoningResult("test", "reasoned", null);
        };
        BrainEngine engine = BrainEngine.createDefault(
                Clock.fixed(Instant.parse("2026-08-29T20:50:00Z"), ZoneOffset.UTC));
        return new RuntimeApprovalConversation(new BrainRuntime(new AssistantCore(engine, reasoning, tools), tools));
    }

    private static RuntimeApprovalConversation recoveryBridge(int[] originalAttempts, int[] secondActions) {
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("zeta_action", false, Set.of("zeta"), Set.of(),
                "recoverable zeta action", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> {
                    originalAttempts[0]++;
                    if (originalAttempts[0] <= 2) return ToolResult.retryableFailure("temporary zeta failure");
                    return ToolResult.success("zeta recovered");
                });
        tools.register(new ToolSpec("send_message", true, Set.of("message", "status"), Set.of(),
                "send status message", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> {
                    secondActions[0]++;
                    return ToolResult.success("Status message sent");
                });

        ReasoningRouter reasoning = request -> {
            String utterance = request.utterance().toLowerCase();
            if (utterance.contains("zeta operation")) {
                return new ReasoningResult("test", "Starting zeta.", new Plan(
                        "perform recoverable zeta action",
                        List.of(new PlanStep("zeta_action", Map.of(), false))));
            }
            if (utterance.contains("status message")) {
                return new ReasoningResult("test", "Sending status.", new Plan(
                        "send second consequential message",
                        List.of(new PlanStep("send_message", Map.of(), true))));
            }
            return new ReasoningResult("test", "reasoned", null);
        };
        BrainEngine engine = BrainEngine.createDefault(
                Clock.fixed(Instant.parse("2026-08-29T20:51:00Z"), ZoneOffset.UTC));
        return new RuntimeApprovalConversation(new BrainRuntime(new AssistantCore(engine, reasoning, tools), tools));
    }

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
