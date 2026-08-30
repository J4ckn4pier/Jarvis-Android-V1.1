package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;

public final class ExecutionCursor {
    private final Plan plan;
    private int nextStepIndex;
    private final List<String> outputs = new ArrayList<>();

    ExecutionCursor(Plan plan) { this.plan = plan; }
    public Plan plan() { return plan; }
    public int nextStepIndex() { return nextStepIndex; }
    public List<String> outputs() { return List.copyOf(outputs); }
    void advance(String output) { outputs.add(output); nextStepIndex++; }
}
