package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

public final class AssistantContextIntegrationTest {
    private static int checks;

    public static void main(String[] args) {
        relevantLongTermMemoryReachesReasoningCortex();
        irrelevantMemoryDoesNotPolluteReasoningContext();
        failingOptionalContextDoesNotTakeDownReasoning();
        System.out.println("AssistantContextIntegrationTest: " + checks + " assertions passed");
    }

    private static void relevantLongTermMemoryReachesReasoningCortex() {
        Instant now = Instant.parse("2026-08-27T23:30:00Z");
        LongTermMemoryStore store = new LongTermMemoryStore();
        store.put(new RichMemory("date.restaurant", MemoryType.PREFERENCE,
                "Prefers quiet Italian restaurants for date night", "user-stated", 1.0, 0.95,
                now.minusSeconds(86400), null, Set.of("date", "night", "restaurant", "italian", "quiet")));
        final ReasoningRequest[] seen = {null};
        ReasoningRouter router = request -> {
            seen[0] = request;
            return new ReasoningResult("capture", "I have the context.", null);
        };
        AssistantContextSource memory = new MemoryContextSource(store, Clock.fixed(now, ZoneOffset.UTC), 5);
        AssistantCore core = new AssistantCore(
                BrainEngine.createDefault(Clock.fixed(now, ZoneOffset.UTC)), router, ToolRegistry.standard(), memory);
        core.handle("Hey Jarvis");
        core.handle("help me plan date night");
        check(seen[0] != null, "open-ended request should reach reasoning cortex");
        check(seen[0].context().contains("quiet Italian restaurants"),
                "relevant durable preference should reach provider context automatically");
    }

    private static void irrelevantMemoryDoesNotPolluteReasoningContext() {
        Instant now = Instant.parse("2026-08-27T23:30:00Z");
        LongTermMemoryStore store = new LongTermMemoryStore();
        store.put(new RichMemory("hardware.keyboard", MemoryType.FACT,
                "Owns a mechanical keyboard", "user-stated", 1.0, 0.5,
                now.minusSeconds(86400), null, Set.of("keyboard", "hardware")));
        final ReasoningRequest[] seen = {null};
        ReasoningRouter router = request -> {
            seen[0] = request;
            return new ReasoningResult("capture", "Okay.", null);
        };
        AssistantCore core = new AssistantCore(
                BrainEngine.createDefault(Clock.fixed(now, ZoneOffset.UTC)), router, ToolRegistry.standard(),
                new MemoryContextSource(store, Clock.fixed(now, ZoneOffset.UTC), 5));
        core.handle("Hey Jarvis");
        core.handle("help me plan date night");
        check(seen[0] != null && !seen[0].context().contains("mechanical keyboard"),
                "unrelated long-term memory must not be stuffed into every prompt");
    }

    private static void failingOptionalContextDoesNotTakeDownReasoning() {
        Instant now = Instant.parse("2026-08-27T23:30:00Z");
        final ReasoningRequest[] seen = {null};
        ReasoningRouter router = request -> {
            seen[0] = request;
            return new ReasoningResult("capture", "I can still reason without optional context.", null);
        };
        AssistantContextSource brokenContext = utterance -> { throw new IllegalStateException("memory store unavailable"); };
        AssistantCore core = new AssistantCore(
                BrainEngine.createDefault(Clock.fixed(now, ZoneOffset.UTC)), router, ToolRegistry.standard(), brokenContext);
        core.handle("Hey Jarvis");
        BrainResponse response;
        try {
            response = core.handle("help me reason about a hard decision");
        } catch (RuntimeException failure) {
            throw new AssertionError("optional durable/device context failure must not take down shared reasoning", failure);
        }
        check(seen[0] != null, "reasoning should proceed when optional context is unavailable");
        check(response.text().contains("still reason"), "assistant should return the valid provider answer without optional context");
        check(seen[0].context().contains("Recent conversation:"),
                "conversation context should still be preserved when optional durable context fails");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
