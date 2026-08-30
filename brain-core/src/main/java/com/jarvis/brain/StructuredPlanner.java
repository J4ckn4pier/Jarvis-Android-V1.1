package com.jarvis.brain;

public final class StructuredPlanner {
    private final PlanValidator validator;
    public StructuredPlanner(ToolRegistry registry) { this.validator = new PlanValidator(registry); }

    public PlanValidation validateModelPlan(String modelJson) {
        try {
            return validator.validate(PlanJsonCodec.decode(modelJson));
        } catch (RuntimeException malformed) {
            return new PlanValidation(false, new Plan("invalid model plan", java.util.List.of()),
                    java.util.List.of("Malformed model plan: " + malformed.getMessage()));
        }
    }
}
