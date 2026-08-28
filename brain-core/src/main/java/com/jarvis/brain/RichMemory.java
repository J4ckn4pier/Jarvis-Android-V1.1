package com.jarvis.brain;

import java.time.Instant;
import java.util.Set;

public record RichMemory(String key, MemoryType type, String content, String source,
                         double confidence, double importance, Instant validFrom,
                         Instant validUntil, Set<String> tags, int evidenceCount,
                         Instant lastAccessedAt) {
    public RichMemory {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("memory key required");
        type = type == null ? MemoryType.FACT : type;
        content = content == null ? "" : content;
        source = source == null ? "unknown" : source;
        confidence = clamp(confidence);
        importance = clamp(importance);
        validFrom = validFrom == null ? Instant.MIN : validFrom;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        evidenceCount = Math.max(1, evidenceCount);
        lastAccessedAt = lastAccessedAt == null || lastAccessedAt.isBefore(validFrom) ? validFrom : lastAccessedAt;
    }

    public RichMemory(String key, MemoryType type, String content, String source,
                      double confidence, double importance, Instant validFrom,
                      Instant validUntil, Set<String> tags) {
        this(key, type, content, source, confidence, importance, validFrom, validUntil, tags, 1, validFrom);
    }

    public RichMemory(String key, MemoryType type, String content, String source,
                      double confidence, double importance, Instant validFrom,
                      Instant validUntil, Set<String> tags, int evidenceCount) {
        this(key, type, content, source, confidence, importance, validFrom, validUntil, tags, evidenceCount, validFrom);
    }

    public boolean validAt(Instant when) {
        return !when.isBefore(validFrom) && (validUntil == null || when.isBefore(validUntil));
    }

    public RichMemory closeAt(Instant when) {
        return new RichMemory(key, type, content, source, confidence, importance, validFrom, when, tags,
                evidenceCount, lastAccessedAt);
    }

    public RichMemory withEvidence(int count, double newConfidence) {
        return new RichMemory(key, type, content, source, newConfidence, importance, validFrom, validUntil, tags,
                count, lastAccessedAt);
    }

    public RichMemory touch(Instant when) {
        if (when == null || !when.isAfter(lastAccessedAt)) return this;
        return new RichMemory(key, type, content, source, confidence, importance, validFrom, validUntil, tags,
                evidenceCount, when);
    }

    private static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }
}
