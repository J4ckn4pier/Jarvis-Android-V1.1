package com.jarvis.brain;

import java.util.List;
import java.util.Locale;

/** Bounded model-plan repair loop. Invalid model plans are never executable. */
public final class PlanRepairCoordinator {
    private final PlanTextValidator validator;
    private final int maxAttempts;

    public PlanRepairCoordinator(PlanTextValidator validator, int maxAttempts) {
        if (validator == null) throw new IllegalArgumentException("validator required");
        this.validator = validator;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public PlanRepairResult plan(String goal, String context, ModelPlanGenerator generator) {
        if (generator == null) throw new IllegalArgumentException("generator required");
        String prompt = initialPrompt(goal, context);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String generated;
            try {
                generated = generator.generate(prompt);
            } catch (RuntimeException providerFailure) {
                String detail = providerFailure.getMessage() == null
                        ? providerFailure.getClass().getSimpleName()
                        : providerFailure.getMessage();
                return new PlanRepairResult(PlanRepairResult.Status.NEEDS_CLARIFICATION,
                        null, attempt, List.of("Plan generation failed: " + detail),
                        "I couldn't generate a safe plan right now, so I did not prepare any action.");
            }
            PlanValidation validation;
            try {
                validation = validator.validate(generated);
            } catch (RuntimeException validationFailure) {
                String detail = validationFailure.getMessage() == null
                        ? validationFailure.getClass().getSimpleName()
                        : validationFailure.getMessage();
                return validationUnavailable(attempt, detail);
            }
            if (validation == null) {
                return validationUnavailable(attempt, "validator returned no verdict");
            }
            if (validation.valid()) {
                if (validation.effectivePlan() == null) {
                    return validationUnavailable(attempt, "validator approved no effective plan");
                }
                return new PlanRepairResult(PlanRepairResult.Status.VALID,
                        validation.effectivePlan(), attempt, List.of(), "");
            }
            if (attempt < maxAttempts) {
                prompt = repairPrompt(goal, context, validation.errors());
            } else {
                return new PlanRepairResult(PlanRepairResult.Status.NEEDS_CLARIFICATION,
                        null, attempt, validation.errors(), clarification(validation.errors()));
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static PlanRepairResult validationUnavailable(int attempt, String detail) {
        return new PlanRepairResult(PlanRepairResult.Status.NEEDS_CLARIFICATION,
                null, attempt, List.of("Plan validation failed: " + detail),
                "I couldn't validate a safe plan right now, so I did not prepare any action.");
    }

    private static String initialPrompt(String goal, String context) {
        return "Create a structured JARVIS tool plan for this goal: " + safe(goal)
                + "\nContext: " + safe(context)
                + "\nUse only registered tools and provide all required arguments. Do not invent tools.";
    }

    private static String repairPrompt(String goal, String context, List<String> errors) {
        return initialPrompt(goal, context)
                + "\nThe previous plan was rejected by the deterministic validator. "
                + "Fix every error and return a corrected plan only.\nValidation errors:\n- "
                + String.join("\n- ", errors);
    }

    private static String clarification(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "I need one more detail before I can build a safe plan.";
        }
        String joined = String.join("; ", errors);
        String lower = joined.toLowerCase(Locale.ROOT);
        int marker = lower.indexOf("missing required argument:");
        if (marker >= 0) {
            String value = joined.substring(marker + "missing required argument:".length()).trim();
            int semi = value.indexOf(';');
            if (semi >= 0) value = value.substring(0, semi).trim();
            return "I need the " + value + " before I can continue.";
        }
        return "I need a little more information before I can continue safely: " + joined;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}