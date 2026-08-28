package com.jarvis.brain;

import java.time.Instant;
import java.util.Set;

/** Converts explicit post-outcome feedback into trusted memory linked to its originating episode. */
public final class OutcomeFeedbackMemory {
    private final LongTermMemoryStore store;

    public OutcomeFeedbackMemory(LongTermMemoryStore store) {
        if (store == null) throw new IllegalArgumentException("memory store required");
        this.store = store;
    }

    public void recordExplicitFeedback(RecommendationEpisode episode, String feedback, Instant observedAt) {
        if (episode == null) throw new IllegalArgumentException("episode required");
        String text = feedback == null ? "" : feedback.trim();
        if (text.isBlank()) throw new IllegalArgumentException("explicit feedback required");
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
}
