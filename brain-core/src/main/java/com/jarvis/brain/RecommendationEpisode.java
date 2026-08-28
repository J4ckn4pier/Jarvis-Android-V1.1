package com.jarvis.brain;

import java.time.Instant;

/** A recommendation/action episode that can be followed up regardless of domain. */
public record RecommendationEpisode(String id, String domain, String subject, Instant recommendedAt) {
    public RecommendationEpisode {
        id = clean(id);
        domain = clean(domain);
        subject = clean(subject);
        if (id.isBlank()) throw new IllegalArgumentException("episode id required");
        if (domain.isBlank()) throw new IllegalArgumentException("episode domain required");
        if (subject.isBlank()) throw new IllegalArgumentException("episode subject required");
        recommendedAt = recommendedAt == null ? Instant.now() : recommendedAt;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
