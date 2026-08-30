package com.jarvis.brain;

import java.util.List;

public record Plan(String goal, List<PlanStep> steps) {
    public boolean requiresApproval() {
        return steps.stream().anyMatch(PlanStep::consequential);
    }
}
