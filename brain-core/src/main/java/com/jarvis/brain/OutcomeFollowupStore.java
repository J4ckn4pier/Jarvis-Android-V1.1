package com.jarvis.brain;

import java.time.Instant;
import java.util.List;

/** Persistence boundary for acted-on episodes awaiting outcome feedback. */
public interface OutcomeFollowupStore {
    record Entry(RecommendationEpisode episode, Instant actedAt) {
        public Entry {
            if (episode == null) throw new IllegalArgumentException("episode required");
            if (actedAt == null) throw new IllegalArgumentException("actedAt required");
        }
    }

    List<Entry> loadAll();
    void upsert(Entry entry);
    void remove(String episodeId);
}
