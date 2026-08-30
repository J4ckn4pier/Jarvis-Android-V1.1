package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A discarded retryable side task must never sound like it was secretly queued for later recovery. */
public final class PendingSideRecoveryTruthfulnessTest {
    private static int checks;

    public static void main(String[] args) {
        retryableSideFailureIsReportedAsNotQueued();
        System.out.println("PendingSideRecoveryTruthfulnessTest: " + checks + " assertions passed");
    }

    private static void retryableSideFailureIsReportedAsNotQueued() {
        int[] sends = {0};
        int[] sideAttempts = {0};
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"),
                "send", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> {
                    sends[0]++;
                    return ToolResult.success("Message sent");
                });
        tools.register(new ToolSpec("read_flaky_status", false, Set.of(), Set.of(),
                "read flaky status", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> {
                    sideAttempts[0]++;
                    return ToolResult.retryableFailure("temporary side-service outage");
                });
        ReasoningRouter reasoning = request -> {
            if (request.utterance().toLowerCase().contains("flaky status")) {
                return new ReasoningResult("test", "Checking.", new Plan(
                        "check flaky side status", List.of(new PlanStep("read_flaky_status", Map.of(), false))));
            }
            return new ReasoningResult("test", "reasoned", null);
        };
        BrainEngine engine = BrainEngine.createDefault(
                Clock.fixed(Instant.parse("2026-08-30T16:20:00Z"), ZoneOffset.UTC));
        RuntimeApprovalConversation conversation = new RuntimeApprovalConversation(
                new BrainRuntime(new AssistantCore(engine, reasoning, tools), tools));

        check(conversation.handle("Jarvis, text Mom I am on my way").state() == AssistantSurfaceState.AWAITING_APPROVAL,
                "original consequential action begins pending approval");
        RuntimeSurfacePresentation side = conversation.handle("check the flaky status");

        check(side.state() == AssistantSurfaceState.AWAITING_APPROVAL,
                "original approval remains the authoritative pending decision");
        check(side.primaryAction() == RuntimeSurfaceAction.APPROVE,
                "the only retry/approval affordance still belongs to the original action");
        check(sideAttempts[0] >= 1, "side task really attempted and failed retryably");
        check(side.text().contains("temporary side-service outage"),
                "side failure reason remains visible");
        check(side.text().toLowerCase().contains("not") && side.text().toLowerCase().contains("queue"),
                "JARVIS must say the retryable side task was not queued for later recovery");
        check(conversation.hasPendingApproval() && sends[0] == 0,
                "original consequential action remains pending and unexecuted");
    }

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
