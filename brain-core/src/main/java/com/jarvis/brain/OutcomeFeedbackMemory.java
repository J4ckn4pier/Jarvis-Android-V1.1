package com.jarvis.brain;

import java.time.Instant;
import java.util.Set;

/** Converts post-outcome feedback into memory while preserving attribution trust boundaries. */
public final class OutcomeFeedbackMemory {
    private final LongTermMemoryStore store;

    public OutcomeFeedbackMemory(LongTermMemoryStore store) {
        if (store == null) throw new IllegalArgumentException("memory store required");
        this.store = store;
    }

    /** Direct episode-bound user feedback is trusted user-stated evidence. */
    public void recordExplicitFeedback(RecommendationEpisode episode, String feedback, Instant observedAt) {
        if (episode == null) throw new IllegalArgumentException("episode required");
        String text = requireFeedback(feedback);
        Instant when = observedAt == null ? Instant.now() : observedAt;
        String episodeTag = "episode:" + episode.id();
        Set<String> tags = Set.of(episodeTag, "domain:" + episode.domain(), "explicit-feedback");

        store.put(new RichMemory(
                "recommendation-episode:" + episode.id(), MemoryType.EPISODE,
                "Recommended " + episode.subject() + " (" + episode.domain() + "); user feedback: " + text,
                "user-stated", 1.0, 0.82, when, null, tags));

        store.put(new RichMemory(
                "feedback-preference:" + episode.id(), MemoryType.PREFERENCE,
                text, "user-stated", 1.0, 0.78, when, null, tags));
    }

    /**
     * Spontaneous feedback that JARVIS merely attributes to a prior episode stays an inference.
     * It may be retained for later confirmation, but cannot create trusted preference memory.
     */
    public void recordInferredFeedback(RecommendationEpisode episode, String feedback, double attributionConfidence, Instant observedAt) {
        if (episode == null) throw new IllegalArgumentException("episode required");
        String text = requireFeedback(feedback);
        Instant when = observedAt == null ? Instant.now() : observedAt;
        double confidence = Math.max(0.0, Math.min(1.0, attributionConfidence));
        Set<String> tags = Set.of("episode:" + episode.id(), "domain:" + episode.domain(), "inferred-feedback-attribution");
        store.put(new RichMemory(
                "feedback-inference:" + episode.id(), MemoryType.INFERENCE,
                "Possible feedback about " + episode.subject() + ": " + text,
                "inferred-attribution", confidence, 0.45, when, null, tags));
    }

    private static String requireFeedback(String feedback) {
        String text = feedback == null ? "" : feedback.trim();
        if (text.isBlank()) throw new IllegalArgumentException("feedback required");
        return text;
    }
}
