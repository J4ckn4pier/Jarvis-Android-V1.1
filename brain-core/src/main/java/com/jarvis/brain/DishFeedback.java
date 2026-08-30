package com.jarvis.brain;

import java.util.List;

/** Dish/component-level structured outcome extracted from free-form explicit feedback. */
public record DishFeedback(String dish, String sentiment, List<FeedbackAspect> aspects) {
    public DishFeedback {
        dish = clean(dish, "dish");
        sentiment = clean(sentiment, "sentiment");
        aspects = aspects == null ? List.of() : List.copyOf(aspects);
    }
    private static String clean(String v, String label) {
        String s = v == null ? "" : v.trim();
        if (s.isBlank()) throw new IllegalArgumentException(label + " required");
        return s;
    }
}
