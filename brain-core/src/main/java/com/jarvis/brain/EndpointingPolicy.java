package com.jarvis.brain;

import java.util.Locale;
import java.util.Set;

public final class EndpointingPolicy {
    private static final long COMPLETE_PAUSE_MS = 900;
    private static final long INCOMPLETE_PAUSE_MS = 1800;
    private static final Set<String> TRAILING_CONNECTORS = Set.of(
            "and", "or", "but", "because", "so", "then", "to", "for", "with", "that", "if", "when", "while", "from", "about", "the", "a", "an"
    );
    // Verbs that usually require an object/complement when they are the final token.
    // This is deliberately a small conservative heuristic: the endpointing layer should
    // wait a little longer when syntax strongly suggests the user has not finished, while
    // the hard silence ceiling still guarantees eventual commit.
    private static final Set<String> TRAILING_COMPLEMENT_VERBS = Set.of(
            "find", "get", "make", "tell", "ask", "send", "show", "give",
            "bring", "take", "put", "set", "look", "search", "book", "reserve",
            "schedule", "remind", "navigate", "translate"
    );

    public boolean shouldCommit(String transcript, long silenceMs) {
        String normalized = transcript == null ? "" : transcript.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return false;
        if (silenceMs >= INCOMPLETE_PAUSE_MS) return true;
        if (isLikelyIncomplete(normalized)) return false;
        return silenceMs >= COMPLETE_PAUSE_MS;
    }

    private boolean isLikelyIncomplete(String text) {
        if (text.endsWith(",") || text.endsWith("-") || text.endsWith("…")) return true;
        String[] tokens = text.replaceAll("[^a-z0-9' ]", "").trim().split("\\s+");
        if (tokens.length == 0) return false;
        String last = tokens[tokens.length - 1];
        return TRAILING_CONNECTORS.contains(last) || TRAILING_COMPLEMENT_VERBS.contains(last);
    }
}
