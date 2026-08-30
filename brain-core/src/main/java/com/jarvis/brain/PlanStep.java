package com.jarvis.brain;

import java.util.Map;

public record PlanStep(String tool, Map<String, String> arguments, boolean consequential) {
    public PlanStep(String tool) { this(tool, Map.of(), false); }
}
