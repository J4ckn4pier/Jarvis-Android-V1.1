package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

public final class RuntimeApprovalConversationTest {
    private static int checks;
    public static void main(String[] args) {
        spokenYesApprovesExactlyOnce();
        spokenDeferralCancelsWithoutExecution();
        lowConfidenceApprovalStaysPending();
        retryableConsequentialFailureExplainsFreshApproval();
        System.out.println("RuntimeApprovalConversationTest: " + checks + " assertions passed");
    }

    private static void spokenYesApprovesExactlyOnce() {
        int[] sends = {0};
        RuntimeApprovalConversation bridge = bridge(sends);
        RuntimeSurfacePresentation pending = bridge.handle("Jarvis, text Mom I am on my way");
        check(pending.state() == AssistantSurfaceState.AWAITING_APPROVAL, "message waits for approval");
        RuntimeSurfacePresentation done = bridge.handle("yes");
        check(done.state() == AssistantSurfaceState.ACTION_DONE, "yes resumes pending plan");
        check(sends[0] == 1, "approved tool executes exactly once");
        bridge.handle("yes");
        check(sends[0] == 1, "second yes cannot replay completed action");
    }

    private static void spokenDeferralCancelsWithoutExecution() {
        int[] sends = {0};
        RuntimeApprovalConversation bridge = bridge(sends);
        bridge.handle("Jarvis, message Mom I will be late");
        RuntimeSurfacePresentation cancelled = bridge.handle("not yet");
        check(cancelled.state() == AssistantSurfaceState.IDLE, "deferral returns idle");
        check(sends[0] == 0, "deferral never executes message");
    }

    private static void lowConfidenceApprovalStaysPending() {
        int[] sends = {0};
        RuntimeApprovalConversation bridge = bridge(sends);
        RuntimeSurfacePresentation pending = bridge.handle("Jarvis, text Mom I am on my way");
        check(pending.state() == AssistantSurfaceState.AWAITING_APPROVAL, "message waits for approval before confidence check");
        RuntimeSurfacePresentation unclear = bridge.handle("yes", 0.42);
        check(unclear.state() == AssistantSurfaceState.AWAITING_APPROVAL, "low-confidence yes keeps approval pending");
        check(sends[0] == 0, "low-confidence yes never executes consequential tool");
        RuntimeSurfacePresentation done = bridge.handle("confirm", 0.95);
        check(done.state() == AssistantSurfaceState.ACTION_DONE, "clear explicit confirmation resumes pending plan");
        check(sends[0] == 1, "clear explicit confirmation executes exactly once");
    }

    private static void retryableConsequentialFailureExplainsFreshApproval() {
        int[] sends = {0};
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"),
                "send", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> {
                    sends[0]++;
                    return sends[0] == 1 ? ToolResult.retryableFailure("Messaging service is temporarily busy") : ToolResult.success("Message sent");
                });
        BrainEngine engine = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T23:00:00Z"), ZoneOffset.UTC));
        AssistantCore assistant = new AssistantCore(engine, request -> new ReasoningResult("local", "reasoned", null), tools);
        RuntimeApprovalConversation bridge = new RuntimeApprovalConversation(new BrainRuntime(assistant, tools));

        RuntimeSurfacePresentation pending = bridge.handle("Jarvis, text Mom I am on my way");
        check(pending.state() == AssistantSurfaceState.AWAITING_APPROVAL, "message waits for initial approval");
        RuntimeSurfacePresentation retryApproval = bridge.handle("yes");
        check(retryApproval.state() == AssistantSurfaceState.AWAITING_APPROVAL,
                "retryable consequential failure must return to approval instead of silently retrying");
        check(sends[0] == 1, "first approval authorizes exactly one send attempt");
        String retryText = retryApproval.text().toLowerCase();
        check(retryText.contains("temporarily busy"), "retry approval prompt should explain the failed attempt");
        check(retryText.contains("approval") || retryText.contains("approve") || retryText.contains("retry"),
                "retry approval prompt should explain that another attempt needs a fresh decision");
        RuntimeSurfacePresentation done = bridge.handle("yes");
        check(done.state() == AssistantSurfaceState.ACTION_DONE, "fresh second approval may retry and finish");
        check(sends[0] == 2, "second approval authorizes exactly one retry attempt");
    }

    private static RuntimeApprovalConversation bridge(int[] sends) {
        ToolRegistry tools = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        tools.register(new ToolSpec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"),
                "send", ToolExecutionClass.CONSEQUENTIAL), (args, ctx) -> {
                    sends[0]++;
                    return ToolResult.success("Message sent");
                });
        BrainEngine engine = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T23:00:00Z"), ZoneOffset.UTC));
        AssistantCore assistant = new AssistantCore(engine, request -> new ReasoningResult("local", "reasoned", null), tools);
        return new RuntimeApprovalConversation(new BrainRuntime(assistant, tools));
    }

    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
