package com.jarvis.brain;

/** Transport-neutral request for a conversational outbound call after approval. */
public record ConversationalCallRequest(
        String destination,
        String business,
        String representedUser,
        String preferredTime) {
    public ConversationalCallRequest {
        destination = normalizedRequired(destination, "destination");
        business = normalizedRequired(business, "business");
        representedUser = normalizedRequired(representedUser, "representedUser");
        preferredTime = normalizedRequired(preferredTime, "preferredTime");
    }

    private static String normalizedRequired(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
