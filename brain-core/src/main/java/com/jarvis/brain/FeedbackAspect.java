package com.jarvis.brain;

/** Structured aspect sentiment extracted from explicit outcome feedback. */
public record FeedbackAspect(String aspect, String sentiment) {
    public FeedbackAspect {
        aspect = clean(aspect, "aspect");
        sentiment = clean(sentiment, "sentiment");
    }
    private static String clean(String v, String label) {
        String s = v == null ? "" : v.trim();
        if (s.isBlank()) throw new IllegalArgumentException(label + " required");
        return s;
    }
}
