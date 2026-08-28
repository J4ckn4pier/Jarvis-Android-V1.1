package com.jarvis.brain;

import java.util.List;
import java.util.Map;

public record VisionResult(String id, String summary, List<String> suggestedTasks, Map<String,String> evidence) {
    public VisionResult {
        id = require(id,"id");
        summary = require(summary,"summary");
        suggestedTasks = suggestedTasks == null ? List.of() : List.copyOf(suggestedTasks);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
    private static String require(String v,String label){String s=v==null?"":v.trim();if(s.isBlank())throw new IllegalArgumentException(label+" required");return s;}
}
