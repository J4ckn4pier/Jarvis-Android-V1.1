package com.jarvis.brain;

import java.util.List;

public record PlanRepairResult(Status status, Plan plan, int attempts, List<String> errors, String clarification) {
    public enum Status { VALID, NEEDS_CLARIFICATION }

    public PlanRepairResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        clarification = clarification == null ? "" : clarification;
    }
}
