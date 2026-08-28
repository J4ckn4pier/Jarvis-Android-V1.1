package com.jarvis.brain;

import java.time.Instant;

public final class OutcomeFeedbackMemoryTest {
    public static void main(String[] args) {
        recommendationEpisodeIsDomainAgnostic();
        explicitFeedbackWritesTrustedPreferenceAndEpisode();
        System.out.println("OutcomeFeedbackMemoryTest passed");
    }

    private static void recommendationEpisodeIsDomainAgnostic() {
        RecommendationEpisode restaurant = new RecommendationEpisode("rec-1", "restaurant", "Castle Cafe", Instant.parse("2026-08-28T18:00:00Z"));
        RecommendationEpisode movie = new RecommendationEpisode("rec-2", "movie", "Arrival", Instant.parse("2026-08-28T19:00:00Z"));
        assertEquals("restaurant", restaurant.domain(), "restaurant domain");
        assertEquals("movie", movie.domain(), "movie domain");
    }

    private static void explicitFeedbackWritesTrustedPreferenceAndEpisode() {
        LongTermMemoryStore store = new LongTermMemoryStore();
        OutcomeFeedbackMemory recorder = new OutcomeFeedbackMemory(store);
        RecommendationEpisode episode = new RecommendationEpisode("rec-42", "restaurant", "Castle Cafe", Instant.parse("2026-08-28T18:00:00Z"));
        recorder.recordExplicitFeedback(episode, "I loved the quiet atmosphere and patio", Instant.parse("2026-08-28T21:00:00Z"));

        RichMemory preference = store.current("feedback-preference:rec-42", Instant.parse("2026-08-28T21:01:00Z")).orElseThrow();
        assertEquals(MemoryType.PREFERENCE, preference.type(), "feedback becomes preference memory");
        assertEquals("user-stated", preference.source(), "explicit feedback is trusted user-stated evidence");
        assertTrue(preference.tags().contains("episode:rec-42"), "preference links to originating episode");

        RichMemory outcome = store.history("recommendation-episode:rec-42").get(0);
        assertEquals(MemoryType.EPISODE, outcome.type(), "outcome is retained as episode memory");
        assertTrue(outcome.content().contains("Castle Cafe"), "episode records recommendation subject");
        assertTrue(outcome.content().contains("loved the quiet atmosphere"), "episode records explicit outcome feedback");
    }

    private static void assertTrue(boolean value, String label) { if (!value) throw new AssertionError(label); }
    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
    }
}
