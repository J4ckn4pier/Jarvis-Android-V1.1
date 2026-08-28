package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public final class PendingClarificationTest {
    private static int checks;

    public static void main(String[] args) {
        missingDestinationBecomesNaturalFollowupAndResumesPlan();
        consequentialFlagSurvivesClarificationResume();
        System.out.println("PendingClarificationTest: " + checks + " assertions passed");
    }

    private static void missingDestinationBecomesNaturalFollowupAndResumesPlan() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        final int[] providerCalls = {0};
        ReasoningRouter router = request -> {
            providerCalls[0]++;
            return new ReasoningResult("planner", "I'll route you there.",
                    new Plan("Navigate", List.of(new PlanStep("navigate", Map.of(), false))));
        };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse first = core.handle("take me there");
        check(first.kind() == BrainResponse.Kind.CONVERSATION, "missing destination should ask, not expose invalid action plan");
        check(first.text().toLowerCase().contains("where") || first.text().toLowerCase().contains("destination"),
                "clarification should ask naturally for destination");

        BrainResponse resumed = core.handle("Castle Cafe");
        check(resumed.kind() == BrainResponse.Kind.ACTION_PLAN, "clarification answer should resume pending plan");
        check(resumed.plan().steps().get(0).arguments().get("destination").equals("Castle Cafe"),
                "answer should fill the single missing destination field");
        check(providerCalls[0] == 1, "simple clarification should not restart provider planning");
    }

    private static void consequentialFlagSurvivesClarificationResume() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T23:30:00Z"), ZoneOffset.UTC);
        ToolRegistry tools = ToolRegistry.standard();
        ReasoningRouter router = request -> new ReasoningResult("planner", "I'll prepare the message.",
                new Plan("Message", List.of(new PlanStep("send_message", Map.of("message", "I'm running late"), false))));
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, tools);
        core.handle("Hey Jarvis");
        BrainResponse first = core.handle("tell someone I'm running late");
        check(first.kind() == BrainResponse.Kind.CONVERSATION, "missing recipient should ask for clarification");
        BrainResponse resumed = core.handle("Mom");
        check(resumed.kind() == BrainResponse.Kind.ACTION_PLAN, "recipient answer should resume action plan");
        check(resumed.plan().steps().get(0).consequential(),
                "registry-enforced consequential flag must survive clarification fill");
        check(resumed.plan().requiresApproval(), "resumed outbound message must still require approval");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
