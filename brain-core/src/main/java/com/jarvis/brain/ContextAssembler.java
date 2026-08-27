package com.jarvis.brain;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ContextAssembler {
    public ContextSnapshot assemble(ContextSignals s) {
        Map<String,String> scalars = new LinkedHashMap<>();
        put(scalars, "Current app", s.currentApp());
        put(scalars, "Screen", s.screenSummary());
        put(scalars, "Local time", s.localTime());
        put(scalars, "Location", s.location());
        Map<String,java.util.List<String>> lists = new LinkedHashMap<>();
        if (!s.calendar().isEmpty()) lists.put("Calendar", s.calendar());
        if (!s.notifications().isEmpty()) lists.put("Notifications", s.notifications());
        if (!s.relevantMemories().isEmpty()) lists.put("Relevant memory", s.relevantMemories());
        return new ContextSnapshot(scalars, lists);
    }
    private static void put(Map<String,String> map, String key, String value) { if (value != null && !value.isBlank()) map.put(key, value); }
}
