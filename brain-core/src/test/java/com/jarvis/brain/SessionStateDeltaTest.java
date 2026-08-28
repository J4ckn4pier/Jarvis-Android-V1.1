package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

public final class SessionStateDeltaTest {
    private static int checks;

    public static void main(String[] args) {
        providerCanWriteTypedSessionStateThatSurvivesDialogueEviction();
        laterCorrectionSupersedesActiveEntityWithoutRewritingHistory();
        System.out.println("SessionStateDeltaTest: " + checks + " assertions passed");
    }

    private static void providerCanWriteTypedSessionStateThatSurvivesDialogueEviction() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T02:20:00Z"), ZoneOffset.UTC);
        final int[] calls = {0};
        final String[] laterContext = {""};
        ReasoningRouter router = request -> {
            calls[0]++;
            if (calls[0] == 1) {
                return new ReasoningResult("local", "Got it — Sarah at Castle Cafe tonight.", null,
                        new SessionStateDelta("meet Sarah tonight", "dinner plans",
                                Map.of("person", "Sarah", "meeting_place", "Castle Cafe"),
                                "", "", "", false));
            }
            laterContext[0] = request.context();
            return new ReasoningResult("local", "You said Castle Cafe.", null);
        };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        core.handle("I'm meeting Sarah at Castle Cafe tonight");
        for (int i = 0; i < 10; i++) core.handle("how are you");
        BrainResponse recalled = core.handle("where am I meeting Sarah?");
        check(recalled.kind() == BrainResponse.Kind.CONVERSATION, "later semantic recall should use provider conversation path");
        check(laterContext[0].contains("Sarah"), "typed session entity should survive raw dialogue eviction");
        check(laterContext[0].contains("Castle Cafe"), "typed session place should survive raw dialogue eviction");
        check(laterContext[0].contains("meet Sarah tonight"), "typed active goal should survive raw dialogue eviction");
    }

    private static void laterCorrectionSupersedesActiveEntityWithoutRewritingHistory() {
        ConversationWorkingMemory memory = new ConversationWorkingMemory();
        memory.apply(new SessionStateDelta("meet Sarah", "dinner", Map.of("meeting_place", "Castle Cafe"),
                "Thai", "", "", false));
        memory.apply(new SessionStateDelta("", "", Map.of("meeting_place", "Lost Coffee"),
                "Italian", "", "", false));
        String current = memory.snapshot();
        check(current.contains("Lost Coffee"), "latest entity correction should be active");
        check(!current.contains("Castle Cafe"), "superseded entity should not remain active in session state");
        check(current.contains("Italian"), "latest session preference correction should replace prior preference");
        check(!current.contains("Thai"), "old session preference should not pollute active context");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
