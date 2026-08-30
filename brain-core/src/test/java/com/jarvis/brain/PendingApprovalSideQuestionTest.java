package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A safe side question may be answered while a consequential decision remains pending. */
public final class PendingApprovalSideQuestionTest {
    private static int checks;

    public static void main(String[] args) {
        safeSideQuestionDoesNotLoseApproval();
        abandonedSideClarificationDoesNotLeakPastOriginalApproval();
        System.out.println("PendingApprovalSideQuestionTest: " + checks + " assertions passed");
    }

    private static void safeSideQuestionDoesNotLoseApproval() {
        int[] sends = {0};
        int[] reads = {0};
        RuntimeApprovalConversation conversation = bridge(sends, reads);

        RuntimeSurfacePresentation pending = conversation.handle("Jarvis, text Mom I am on my way");
        check(pending.state() == AssistantSurfaceState.AWAITING_APPROVAL, "message begins as pending approval");
        check(conversation.hasPendingApproval(), "approval is pending before side question");

        RuntimeSurfacePresentation side = conversation.handle("what is the diagnostic value?");
        check(side.state() == AssistantSurfaceState.AWAITING_APPROVAL, "safe side question completes while approval remains surfaced");
        check(side.text().contains("72"), "side question returns its result");
        check(side.primaryAction() == RuntimeSurfaceAction.APPROVE, "approval affordance remains after side answer");
        check(reads[0] == 1, "safe side tool executes exactly once");
        check(conversation.hasPendingApproval(), "original consequential approval survives side question");
        check(sends[0] == 0, "side question never executes pending consequential tool");

        RuntimeSurfacePresentation done = conversation.handle("confirm", 0.95);
        check(done.state() == AssistantSurfaceState.ACTION_DONE, "original approval can still resume after side question");
        check(sends[0] == 1, "original consequential action executes exactly once after explicit approval");
    }

    private static void abandonedSideClarificationDoesNotLeakPastOriginalApproval() {
        int[] sends = {0};
        int[] inspections = {0};
        RuntimeApprovalConversation conversation = clarifyingSideBridge(sends, inspections);

        check(conversation.handle("Jarvis, text Mom I am on my way").state() == AssistantSurfaceState.AWAITING_APPROVAL,
                "original action should begin pending approval");
        RuntimeSurfacePresentation clarification = conversation.handle("inspect something");
        check(clarification.state() == AssistantSurfaceState.AWAITING_APPROVAL,
                "side clarification should not replace the original approval surface");
        check(clarification.text().toLowerCase().contains("topic"),
                "side request should ask for its missing topic before doing anything");
        check(inspections[0] == 0, "incomplete side request must not execute");

        RuntimeSurfacePresentation approved = conversation.handle("confirm", 0.95);
        check(approved.state() == AssistantSurfaceState.ACTION_DONE && sends[0] == 1,
                "confirm should resolve the original pending action");

        RuntimeSurfacePresentation unrelated = conversation.handle("how are you?");
        check(unrelated.text().toLowerCase().contains("i'm doing well"),
                "after resolving the original action, an abandoned side clarification must not consume the next unrelated utterance");
        check(inspections[0] == 0,
                "abandoned side clarification must not execute later using an unrelated sentence as its missing argument");
    }

    private static RuntimeApprovalConversation bridge(int[] sends, int[] reads) {
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"),
                "send", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> {
                    sends[0]++;
                    return ToolResult.success("Message sent");
                });
        tools.register(new ToolSpec("read_info", false, Set.of("diagnostic"), Set.of(),
                "read diagnostic information", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> {
                    reads[0]++;
                    return ToolResult.success("72 diagnostic units");
                });

        ReasoningRouter reasoning = request -> {
            if (request.utterance().toLowerCase().contains("diagnostic value")) {
                return new ReasoningResult("test", "Checking.", new Plan(
                        "answer safe side question",
                        List.of(new PlanStep("read_info", Map.of(), false))));
            }
            return new ReasoningResult("test", "reasoned", null);
        };
        BrainEngine engine = BrainEngine.createDefault(
                Clock.fixed(Instant.parse("2026-08-29T19:55:00Z"), ZoneOffset.UTC));
        AssistantCore assistant = new AssistantCore(engine, reasoning, tools);
        return new RuntimeApprovalConversation(new BrainRuntime(assistant, tools));
    }

    private static RuntimeApprovalConversation clarifyingSideBridge(int[] sends, int[] inspections) {
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"),
                "send", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> {
                    sends[0]++;
                    return ToolResult.success("Message sent");
                });
        tools.register(new ToolSpec("inspect_detail", false, Set.of(), Set.of("topic"),
                "inspect one requested topic", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> {
                    inspections[0]++;
                    return ToolResult.success("inspected=" + args.get("topic"));
                });
        ReasoningRouter reasoning = request -> {
            if (request.utterance().toLowerCase().contains("inspect something")) {
                return new ReasoningResult("test", "I'll inspect it.", new Plan(
                        "inspect side detail", List.of(new PlanStep("inspect_detail", Map.of(), false))));
            }
            return new ReasoningResult("test", "reasoned", null);
        };
        BrainEngine engine = BrainEngine.createDefault(
                Clock.fixed(Instant.parse("2026-08-29T19:56:00Z"), ZoneOffset.UTC));
        return new RuntimeApprovalConversation(new BrainRuntime(new AssistantCore(engine, reasoning, tools), tools));
    }

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
