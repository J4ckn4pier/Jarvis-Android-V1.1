package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConversationContinuityTest {
    private static int checks;

    public static void main(String[] args) {
        providerSeesItsOwnPriorAnswerOnFollowup();
        conversationHistoryKeepsRolesAndRemainsBounded();
        cancelledClarificationRemainsVisibleToLaterReasoning();
        multiStepClarificationPromptsRemainVisibleToLaterReasoning();
        System.out.println("ConversationContinuityTest: " + checks + " assertions passed");
    }

    private static void providerSeesItsOwnPriorAnswerOnFollowup() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        List<ReasoningRequest> requests = new ArrayList<>();
        ReasoningRouter router = request -> {
            requests.add(request);
            if (requests.size() == 1) return new ReasoningResult("local", "The red option is the stronger choice because it is simpler.", null);
            return new ReasoningResult("local", "Yes, the red one.", null);
        };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        core.handle("compare the two options and choose one");
        core.handle("why did you pick that one?");
        check(requests.size() == 2, "both open-ended turns should reach reasoning cortex");
        check(requests.get(1).context().contains("The red option is the stronger choice"),
                "follow-up reasoning must include JARVIS's own prior answer");
        check(requests.get(1).context().contains("compare the two options"),
                "follow-up reasoning must retain prior user turn too");
    }

    private static void conversationHistoryKeepsRolesAndRemainsBounded() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        final ReasoningRequest[] last = {null};
        ReasoningRouter router = request -> {
            last[0] = request;
            return new ReasoningResult("local", "ack-" + request.utterance(), null);
        };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        for (int i = 0; i < 12; i++) core.handle("reason about topic " + i);
        check(last[0].context().contains("USER:"), "conversation context should mark user role");
        check(last[0].context().contains("JARVIS:"), "conversation context should mark assistant role");
        check(!last[0].context().contains("topic 0"), "bounded working context should evict oldest turns");
        check(last[0].context().contains("topic 11"), "bounded working context should retain newest turn");
    }

    private static void cancelledClarificationRemainsVisibleToLaterReasoning() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        List<ReasoningRequest> requests = new ArrayList<>();
        ReasoningRouter router = request -> {
            requests.add(request);
            if (requests.size() == 1) {
                return new ReasoningResult("local", "Where would you like to go?",
                        new Plan("Navigate", List.of(new PlanStep("navigate", Map.of(), false))));
            }
            return new ReasoningResult("local", "Fresh task acknowledged.", null);
        };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse clarification = core.handle("work out a route for me");
        check(clarification.kind() == BrainResponse.Kind.CONVERSATION && core.hasPendingPlan(),
                "missing destination should establish a clarification before cancellation");
        BrainResponse cancelled = core.handle("never mind");
        check(cancelled.text().equals("Cancelled."), "cancellation should terminate the pending clarification");
        core.handle("compare two fresh ideas for me");
        check(requests.size() == 2, "fresh open-ended task should reach reasoning after cancellation");
        check(requests.get(1).context().contains("USER: never mind"),
                "later reasoning must know the user cancelled the prior task");
        check(requests.get(1).context().contains("JARVIS: Cancelled."),
                "later reasoning must know JARVIS acknowledged cancellation");
    }

    private static void multiStepClarificationPromptsRemainVisibleToLaterReasoning() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        List<ReasoningRequest> requests = new ArrayList<>();
        ReasoningRouter router = request -> {
            requests.add(request);
            if (requests.size() == 1) {
                return new ReasoningResult("local", "I'll prepare it.",
                        new Plan("Message", List.of(new PlanStep("send_message", Map.of(), false))));
            }
            return new ReasoningResult("local", "Fresh task acknowledged.", null);
        };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse first = core.handle("prepare a message for someone");
        check(first.kind() == BrainResponse.Kind.CONVERSATION && core.hasPendingPlan(),
                "message with two missing fields should begin clarification");
        BrainResponse second = core.handle("Mom");
        check(second.kind() == BrainResponse.Kind.CONVERSATION && core.hasPendingPlan(),
                "first clarification answer should leave the other required field pending");
        String secondPrompt = second.text();
        BrainResponse completed = core.handle("I'm running late");
        check(completed.kind() == BrainResponse.Kind.ACTION_PLAN && !core.hasPendingPlan(),
                "second clarification answer should complete the safe plan");
        core.handle("compare two unrelated ideas for me");
        check(requests.size() == 2, "fresh reasoning should run after the clarified plan is complete");
        check(requests.get(1).context().contains("JARVIS: " + secondPrompt),
                "later reasoning must retain JARVIS's intermediate clarification prompt, not only user answers");
        check(requests.get(1).context().contains("USER: Mom") && requests.get(1).context().contains("USER: I'm running late"),
                "later reasoning must retain both clarification answers with user provenance");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
