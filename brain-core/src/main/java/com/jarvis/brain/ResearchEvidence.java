package com.jarvis.brain;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Provider-neutral envelope for fresh external evidence. Keeps source, observation
 * time and confidence attached to the payload so the cortex can reason about
 * freshness instead of receiving unqualified text.
 */
public record ResearchEvidence(String payload, String source, String observedAt, double confidence) {
    public ResearchEvidence {
        payload = require(payload, "payload");
        source = require(source, "source");
        observedAt = require(observedAt, "observedAt");
        try {
            Instant.parse(observedAt);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("observedAt must be ISO-8601 instant", e);
        }
        if (!Double.isFinite(confidence)) throw new IllegalArgumentException("confidence must be finite");
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public String toToolOutput() {
        return payload + "|source=" + source + "|observed_at=" + observedAt + "|confidence=" + confidence;
    }

    private static String require(String value, String label) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) throw new IllegalArgumentException(label + " required");
        return clean;
    }
}
