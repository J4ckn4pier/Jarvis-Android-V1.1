package com.jarvis.brain;

import java.util.List;

/**
 * Model-agnostic extraction port. A local/included reasoning provider can implement this
 * with structured output; the memory layer only accepts typed results from this boundary.
 */
public interface OutcomeFeedbackExtractor {
    List<DishFeedback> extractDiningFeedback(String freeText);
}
