package com.jarvis.brain;

import java.time.Instant;
import java.util.List;
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
        recordStructuredExplicitFeedback(episode, feedback, List.of(), observedAt);
    }

    /**
     * Explicit free-form feedback plus typed extraction. The raw statement remains retained as
     * user-stated evidence; dish/component memories are additional grounded preferences linked
     * to the same originating episode, never replacements for the source utterance.
     */
    public void recordStructuredExplicitFeedback(RecommendationEpisode episode, String feedback, List<DishFeedback> structured, Instant observedAt) {
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

        if (structured == null) return;
        int index = 0;
        for (DishFeedback dish : structured) {
            if (dish == null) continue;
            StringBuilder detail = new StringBuilder(dish.dish()).append(": ").append(dish.sentiment());
            for (FeedbackAspect aspect : dish.aspects()) {
                detail.append("; ").append(aspect.aspect()).append("=").append(aspect.sentiment());
            }
            Set<String> dishTags = Set.of(episodeTag, "domain:" + episode.domain(), "explicit-feedback", "dish:" + normalizeTag(dish.dish()));
            store.put(new RichMemory(
                    "feedback-component:" + episode.id() + ":" + index++, MemoryType.PREFERENCE,
                    detail.toString(), "user-stated", 1.0, 0.80, when, null, dishTags));
        }
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

    private static String normalizeTag(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
