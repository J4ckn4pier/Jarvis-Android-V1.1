package com.jarvis.brain;

import java.util.List;
import java.util.Map;

public final class ContextSnapshot {
    private final Map<String, String> scalars;
    private final Map<String, List<String>> lists;
    ContextSnapshot(Map<String, String> scalars, Map<String, List<String>> lists) {
        this.scalars = Map.copyOf(scalars); this.lists = Map.copyOf(lists);
    }
    public Map<String, String> scalars() { return scalars; }
    public Map<String, List<String>> lists() { return lists; }
    public String toPromptText() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String,String> e : scalars.entrySet()) if (!e.getValue().isBlank()) out.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        for (Map.Entry<String,List<String>> e : lists.entrySet()) if (!e.getValue().isEmpty()) out.append(e.getKey()).append(": ").append(String.join(" | ", e.getValue())).append('\n');
        return out.toString().trim();
    }
}
