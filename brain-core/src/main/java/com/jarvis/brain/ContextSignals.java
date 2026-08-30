package com.jarvis.brain;

import java.util.List;

public record ContextSignals(String currentApp, String screenSummary, String localTime,
                             String location, List<String> calendar, List<String> notifications,
                             List<String> relevantMemories) {
    public ContextSignals {
        currentApp = clean(currentApp); screenSummary = clean(screenSummary); localTime = clean(localTime); location = clean(location);
        calendar = copy(calendar); notifications = copy(notifications); relevantMemories = copy(relevantMemories);
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static List<String> copy(List<String> xs) { return xs == null ? List.of() : List.copyOf(xs); }
}
