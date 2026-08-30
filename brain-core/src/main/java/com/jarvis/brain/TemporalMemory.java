package com.jarvis.brain;

import java.time.Instant;

public record TemporalMemory(String key, String value, String source, double confidence,
                             Instant validFrom, Instant validUntil) {
    public TemporalMemory {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("memory key required");
        value = value == null ? "" : value;
        source = source == null ? "unknown" : source;
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        validFrom = validFrom == null ? Instant.MIN : validFrom;
    }
    public boolean validAt(Instant when) {
        return !when.isBefore(validFrom) && (validUntil == null || when.isBefore(validUntil));
    }
}
