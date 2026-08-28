package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class SessionWorkingMemoryTest {
    private static int checks;

    public static void main(String[] args) {
        salientGoalSurvivesRawDialogueEviction();
        latestPreferenceSupersedesSessionPreference();
        System.out.println("SessionWorkingMemoryTest: " + checks + " assertions passed");
    }

    private static void salientGoalSurvivesRawDialogueEviction() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC);
        final String[] seenContext = {""};
        ReasoningRouter router = request -> {
            seenContext[0] = request.context();
            return new ReasoningResult("local", "Based on what you've told me, I'd prioritize dinner planning.", null);
        };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        core.handle("I'm planning dinner in Castle Rock tonight");
        for (int i = 0; i < 10; i++) core.handle("how are you"); // 20 role messages, enough to evict the early turn.
        core.handle("what should I prioritize?");
        check(seenContext[0].contains("Castle Rock"), "structured working memory should retain important location after raw dialogue eviction");
        check(seenContext[0].toLowerCase().contains("dinner"), "structured working memory should retain the active dinner goal");
    }

    private static void latestPreferenceSupersedesSessionPreference() {
        ConversationWorkingMemory memory = new ConversationWorkingMemory();
        memory.observeUserTurn("I prefer Thai food for dinner");
        memory.observeUserTurn("Actually I prefer Italian food for dinner tonight");
        String snapshot = memory.snapshot();
        check(snapshot.toLowerCase().contains("italian"), "latest explicit session preference should be retained");
        check(!snapshot.toLowerCase().contains("thai"), "superseded session preference should not pollute active working memory");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
