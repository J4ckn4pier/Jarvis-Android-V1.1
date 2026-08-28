package com.jarvis.brain;

public final class PlanExecutionClassifier {
    private final ToolRegistry tools;

    public PlanExecutionClassifier(ToolRegistry tools) {
        if (tools == null) throw new IllegalArgumentException("tool registry required");
        this.tools = tools;
    }

    public boolean containsAutonomousResearch(Plan plan) {
        if (plan == null || plan.steps().isEmpty()) return false;
        for (PlanStep step : plan.steps()) {
            ToolRegistry.RegisteredTool registered = tools.resolve(step.tool()).orElse(null);
            if (registered != null && registered.spec().executionClass() == ToolExecutionClass.AUTONOMOUS_RESEARCH) return true;
        }
        return false;
    }
}
