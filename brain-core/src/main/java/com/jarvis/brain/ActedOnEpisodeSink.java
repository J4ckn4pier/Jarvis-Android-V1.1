package com.jarvis.brain;

import java.time.Instant;

/** Receives an episode only after a shared runtime plan has actually completed. */
@FunctionalInterface
public interface ActedOnEpisodeSink {
    void recordActedOn(RecommendationEpisode episode, Instant actedAt);

    static ActedOnEpisodeSink none() {
        return (episode, actedAt) -> { };
    }
}
