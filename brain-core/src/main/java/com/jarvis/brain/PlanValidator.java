package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlanValidator {
    private final ToolRegistry registry;
    public PlanValidator(ToolRegistry registry) { this.registry = registry; }

    public PlanValidation validate(Plan plan) {
        if (plan == null) {
            return new PlanValidation(false, null, List.of("Plan is missing"));
        }
        if (plan.steps() == null) {
            return new PlanValidation(false, new Plan(plan.goal(), List.of()), List.of("Plan step list is missing"));
        }
        if (plan.steps().isEmpty()) {
            return new PlanValidation(false, new Plan(plan.goal(), List.of()), List.of("Plan contains no executable steps"));
        }

        List<String> errors = new ArrayList<>();
        List<PlanStep> effective = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            if (step == null) {
                errors.add("Plan contains a missing step");
                continue;
            }
            if (step.tool() == null || step.tool().isBlank()) {
                errors.add("Plan step is missing a tool name");
                continue;
            }
            ToolRegistry.RegisteredTool registered = registry.resolve(step.tool()).orElse(null);
            if (registered == null) {
                errors.add("Unknown tool: " + step.tool());
                continue;
            }
            ToolSpec spec = registered.spec();
            Map<String, String> arguments = step.arguments();
            if (arguments == null) {
                errors.add("Missing arguments map for " + spec.name());
                arguments = Map.of();
            }
            for (String required : spec.requiredArguments()) {
                String value = arguments.get(required);
                if (value == null || value.isBlank()) errors.add("Missing required argument '" + required + "' for " + spec.name());
            }
            effective.add(new PlanStep(spec.name(), arguments, step.consequential() || spec.consequential()));
        }
        Plan effectivePlan = new Plan(plan.goal(), List.copyOf(effective));
        return new PlanValidation(errors.isEmpty(), effectivePlan, errors);
    }
}