package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;

public final class PlanValidator {
    private final ToolRegistry registry;
    public PlanValidator(ToolRegistry registry) { this.registry = registry; }
    public PlanValidation validate(Plan plan) {
        List<String> errors = new ArrayList<>();
        List<PlanStep> effective = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            ToolRegistry.RegisteredTool registered = registry.resolve(step.tool()).orElse(null);
            if (registered == null) { errors.add("Unknown tool: " + step.tool()); continue; }
            ToolSpec spec = registered.spec();
            for (String required : spec.requiredArguments()) {
                String value = step.arguments().get(required);
                if (value == null || value.isBlank()) errors.add("Missing required argument '" + required + "' for " + spec.name());
            }
            effective.add(new PlanStep(spec.name(), step.arguments(), step.consequential() || spec.consequential()));
        }
        Plan effectivePlan = new Plan(plan.goal(), List.copyOf(effective));
        return new PlanValidation(errors.isEmpty(), effectivePlan, errors);
    }
}
