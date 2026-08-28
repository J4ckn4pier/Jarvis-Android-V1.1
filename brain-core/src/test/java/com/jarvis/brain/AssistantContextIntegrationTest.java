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

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
