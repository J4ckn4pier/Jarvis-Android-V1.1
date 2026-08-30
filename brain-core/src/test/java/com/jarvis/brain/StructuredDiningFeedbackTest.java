package com.jarvis.brain;

import java.time.Instant;
import java.util.List;

public final class StructuredDiningFeedbackTest {
    public static void main(String[] args) {
        LongTermMemoryStore store = new LongTermMemoryStore();
        OutcomeFeedbackMemory memory = new OutcomeFeedbackMemory(store);
        RecommendationEpisode episode = new RecommendationEpisode("dinner-1", "dining", "Example Bistro", Instant.parse("2026-08-28T01:00:00Z"));
        List<DishFeedback> extracted = List.of(
                new DishFeedback("lobster ragu", "positive", List.of(new FeedbackAspect("tenderness","positive"), new FeedbackAspect("vodka sauce","positive"))),
                new DishFeedback("mushroom side", "negative", List.of(new FeedbackAspect("doneness","undercooked")))
        );
        memory.recordStructuredExplicitFeedback(episode, "lobster ragu was excellent; mushroom side undercooked", extracted, Instant.parse("2026-08-28T03:00:00Z"));
        String pack = store.memoryPack("lobster ragu mushroom side", Instant.parse("2026-08-28T04:00:00Z"), 10);
        check(pack.contains("lobster ragu"), "dish must be recallable");
        check(pack.contains("vodka sauce"), "positive aspect must be recallable");
        check(pack.contains("mushroom side"), "negative dish must be recallable");
        check(pack.contains("undercooked"), "negative aspect must be recallable");
        System.out.println("StructuredDiningFeedbackTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
