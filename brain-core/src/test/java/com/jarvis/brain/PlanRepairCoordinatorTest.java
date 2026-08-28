package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;

public final class PlanRepairCoordinatorTest {
    private static int checks = 0;

    public static void main(String[] args) {
        repairsInvalidPlanWithValidationFeedback();
        refusesInvalidPlanAfterBudget();
        System.out.println("PlanRepairCoordinatorTest: " + checks + " assertions passed");
    }

    private static void repairsInvalidPlanWithValidationFeedback() {
        List<String> prompts = new ArrayList<>();
        ModelPlanGenerator generator = prompt -> {
            prompts.add(prompt);
            return prompts.size() == 1 ? "bad" : "good";
        };
        Plan good = new Plan("goal", List.of(new PlanStep("weather_lookup")));
        PlanTextValidator validator = json -> "good".equals(json)
                ? new PlanValidation(true, good, List.of())
                : new PlanValidation(false, new Plan("invalid", List.of()),
                List.of("Unknown tool: imaginary_tool"));

        PlanRepairResult out = new PlanRepairCoordinator(validator, 2)
                .plan("what's the weather tomorrow?", "Castle Rock", generator);

        check(out.status() == PlanRepairResult.Status.VALID, "second plan should validate");
        check(out.plan() == good, "validated effective plan should be returned");
        check(prompts.size() == 2, "one repair attempt should be made");
        check(prompts.get(1).contains("Unknown tool: imaginary_tool"),
                "repair prompt must include exact validator error");
        check(prompts.get(1).contains("Do not invent tools"),
                "repair prompt should explicitly constrain tool invention");
    }

    private static void refusesInvalidPlanAfterBudget() {
        ModelPlanGenerator generator = prompt -> "always-bad";
        PlanTextValidator validator = json -> new PlanValidation(false,
                new Plan("invalid", List.of()), List.of("Missing required argument: destination"));

        PlanRepairResult out = new PlanRepairCoordinator(validator, 2)
                .plan("take me there", "", generator);

        check(out.status() == PlanRepairResult.Status.NEEDS_CLARIFICATION,
                "invalid model output must never leak as executable plan");
        check(out.plan() == null, "no plan should be returned after failed repair budget");
        check(out.attempts() == 2, "attempt budget must be bounded");
        check(out.clarification().toLowerCase().contains("destination"),
                "clarification should expose what is missing");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
