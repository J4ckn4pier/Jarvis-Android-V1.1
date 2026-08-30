package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A safe side question may be answered while a retry/recovery decision remains pending. */
public final class PendingRecoverySideQuestionTest {
    private static int checks;

    public static void main(String[] args) {
        safeSideQuestionDoesNotLoseRecovery();
        abandonedSideClarificationDoesNotLeakPastRetry();
        System.out.println("PendingRecoverySideQuestionTest: " + checks + " assertions passed");
    }

    private static void safeSideQuestionDoesNotLoseRecovery() {
        int[] actionAttempts = {0};
        int[] reads = {0};
        RuntimeApprovalConversation conversation = bridge(actionAttempts, reads);

        RuntimeSurfacePresentation pending = conversation.handle("Jarvis, perform zeta operation");
        check(pending.state() == AssistantSurfaceState.NEEDS_INPUT, "retryable action begins as pending recovery");
        check(conversation.hasPendingRecovery(), "recovery is pending before side question");
        check(actionAttempts[0] == 2, "initial execution uses bounded automatic retry before asking user");

        RuntimeSurfacePresentation side = conversation.handle("what is the diagnostic value?");
        check(side.state() == AssistantSurfaceState.NEEDS_INPUT, "safe side question completes while recovery remains surfaced");
        check(side.text().contains("72"), "side question returns its result");
        check(side.primaryAction() == RuntimeSurfaceAction.RETRY, "retry affordance remains after side answer");
        check(reads[0] == 1, "safe side tool executes exactly once");
        check(conversation.hasPendingRecovery(), "original recovery decision survives side question");
        check(actionAttempts[0] == 2, "side question never retries original action implicitly");

        RuntimeSurfacePresentation done = conversation.handle("retry", 0.95);
        check(done.state() == AssistantSurfaceState.ACTION_DONE, "explicit retry can still resume original action after side question");
        check(actionAttempts[0] == 3, "original action resumes exactly once after explicit retry");
    }

    private static void abandonedSideClarificationDoesNotLeakPastRetry() {
        int[] actionAttempts = {0};
        int[] inspections = {0};
        RuntimeApprovalConversation conversation = clarifyingSideBridge(actionAttempts, inspections);

        check(conversation.handle("Jarvis, perform zeta operation").state() == AssistantSurfaceState.NEEDS_INPUT,
                "original recoverable action should reach explicit retry decision");
        RuntimeSurfacePresentation clarification = conversation.handle("inspect something");
        check(clarification.state() == AssistantSurfaceState.NEEDS_INPUT,
                "side clarification should keep the original recovery surface visible");
        check(clarification.text().toLowerCase().contains("topic"),
                "side request should ask for its missing topic");
        check(inspections[0] == 0, "incomplete side request must not execute");

        RuntimeSurfacePresentation retried = conversation.handle("retry", 0.95);
        check(retried.state() == AssistantSurfaceState.ACTION_DONE && actionAttempts[0] == 3,
                "explicit retry should resolve only the original action");

        RuntimeSurfacePresentation unrelated = conversation.handle("how are you?");
        check(unrelated.text().toLowerCase().contains("i'm doing well"),
                "after resolving recovery, an abandoned side clarification must not consume the next unrelated utterance");
        check(inspections[0] == 0,
                "abandoned recovery-side clarification must not execute later using unrelated speech as its missing argument");
    }

    private static RuntimeApprovalConversation bridge(int[] actionAttempts, int[] reads) {
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("zeta_action", false, Set.of("zeta"), Set.of(),
                "recoverable zeta action", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> {
                    actionAttempts[0]++;
                    if (actionAttempts[0] <= 2) return ToolResult.retryableFailure("temporary zeta failure");
                    return ToolResult.success("zeta recovered");
                });
        tools.register(new ToolSpec("read_info", false, Set.of("diagnostic"), Set.of(),
                "read diagnostic information", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> {
                    reads[0]++;
                    return ToolResult.success("72 diagnostic units");
                });

        ReasoningRouter reasoning = request -> {
            String utterance = request.utterance().toLowerCase();
            if (utterance.contains("zeta operation")) {
                return new ReasoningResult("test", "Starting zeta.", new Plan(
                        "perform recoverable zeta action",
                        List.of(new PlanStep("zeta_action", Map.of(), false))));
            }
            if (utterance.contains("diagnostic value")) {
                return new ReasoningResult("test", "Checking.", new Plan(
                        "answer safe side question",
                        List.of(new PlanStep("read_info", Map.of(), false))));
            }
            return new ReasoningResult("test", "reasoned", null);
        };
        BrainEngine engine = BrainEngine.createDefault(
                Clock.fixed(Instant.parse("2026-08-29T20:05:00Z"), ZoneOffset.UTC));
        AssistantCore assistant = new AssistantCore(engine, reasoning, tools);
        return new RuntimeApprovalConversation(new BrainRuntime(assistant, tools));
    }

    private static RuntimeApprovalConversation clarifyingSideBridge(int[] actionAttempts, int[] inspections) {
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("zeta_action", false, Set.of("zeta"), Set.of(),
                "recoverable zeta action", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> {
                    actionAttempts[0]++;
                    if (actionAttempts[0] <= 2) return ToolResult.retryableFailure("temporary zeta failure");
                    return ToolResult.success("zeta recovered");
                });
        tools.register(new ToolSpec("inspect_detail", false, Set.of(), Set.of("topic"),
                "inspect one requested topic", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> {
                    inspections[0]++;
                    return ToolResult.success("inspected=" + args.get("topic"));
                });
        ReasoningRouter reasoning = request -> {
            String utterance = request.utterance().toLowerCase();
            if (utterance.contains("zeta operation")) {
                return new ReasoningResult("test", "Starting zeta.", new Plan(
                        "perform recoverable zeta action",
                        List.of(new PlanStep("zeta_action", Map.of(), false))));
            }
            if (utterance.contains("inspect something")) {
                return new ReasoningResult("test", "I'll inspect it.", new Plan(
                        "inspect side detail", List.of(new PlanStep("inspect_detail", Map.of(), false))));
            }
            return new ReasoningResult("test", "reasoned", null);
        };
        BrainEngine engine = BrainEngine.createDefault(
                Clock.fixed(Instant.parse("2026-08-29T20:06:00Z"), ZoneOffset.UTC));
        return new RuntimeApprovalConversation(new BrainRuntime(new AssistantCore(engine, reasoning, tools), tools));
    }

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
