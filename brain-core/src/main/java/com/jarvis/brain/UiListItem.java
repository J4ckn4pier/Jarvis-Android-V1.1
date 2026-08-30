package com.jarvis.brain;

import java.util.Map;

/** Generic editable row model used by user-customizable list surfaces. */
public record UiListItem(String id, String title, String details, boolean completed, Map<String,String> attributes) {
    public UiListItem {
        id = require(id, "id");
        title = require(title, "title");
        details = details == null ? "" : details.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
    public UiListItem withCompleted(boolean value) { return new UiListItem(id, title, details, value, attributes); }
    private static String require(String value, String label) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) throw new IllegalArgumentException(label + " required");
        return clean;
    }
}
