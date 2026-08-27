package com.jarvis.brain;

import java.util.List;

public record PlanValidation(boolean valid, Plan effectivePlan, List<String> errors) {
    public PlanValidation { errors = errors == null ? List.of() : List.copyOf(errors); }
}
