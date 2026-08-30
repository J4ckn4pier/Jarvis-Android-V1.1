package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;

public final class PlanRepairCoordinatorTest {
    private static int checks = 0;

    public static void main(String[] args) {
        repairsInvalidPlanWithValidationFeedback();
        refusesInvalidPlanAfterBudget();
        generatorFailureFailsClosedWithoutExecutablePlan();
        validatorFailureFailsClosedWithoutExecutablePlan();
        nullValidatorResultFailsClosedWithoutExecutablePlan();
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

    private static void generatorFailureFailsClosedWithoutExecutablePlan() {
        ModelPlanGenerator generator = prompt -> { throw new RuntimeException("provider unavailable"); };
        PlanTextValidator validator = json -> new PlanValidation(true,
                new Plan("must-never-run", List.of(new PlanStep("send_message"))), List.of());
        PlanRepairResult out;
        try {
            out = new PlanRepairCoordinator(validator, 2).plan("send something", "", generator);
        } catch (RuntimeException escaped) {
            throw new AssertionError("plan generation failure must not escape the repair boundary", escaped);
        }
        check(out.status() == PlanRepairResult.Status.NEEDS_CLARIFICATION,
                "provider failure should yield a non-executable safe result");
        check(out.plan() == null, "provider failure must never manufacture an executable plan");
        check(out.attempts() == 1, "provider failure should stop immediately rather than repeatedly hammering the same failed source");
        check(out.clarification().toLowerCase().contains("couldn't") || out.clarification().toLowerCase().contains("could not"),
                "provider failure should truthfully say that planning could not complete");
    }

    private static void validatorFailureFailsClosedWithoutExecutablePlan() {
        ModelPlanGenerator generator = prompt -> "candidate-plan";
        PlanTextValidator validator = json -> { throw new RuntimeException("plan parser crashed"); };
        PlanRepairResult out;
        try {
            out = new PlanRepairCoordinator(validator, 2).plan("send something", "", generator);
        } catch (RuntimeException escaped) {
            throw new AssertionError("plan validation failure must not escape the repair boundary", escaped);
        }
        check(out.status() == PlanRepairResult.Status.NEEDS_CLARIFICATION,
                "validator failure should yield a non-executable safe result");
        check(out.plan() == null, "validator failure must never leak an unvalidated executable plan");
        check(out.attempts() == 1, "validator failure should stop immediately instead of retrying an unavailable validator");
        String explanation = out.clarification().toLowerCase();
        check(explanation.contains("couldn't") || explanation.contains("could not"),
                "validator failure should truthfully say safe planning could not complete");
    }

    private static void nullValidatorResultFailsClosedWithoutExecutablePlan() {
        ModelPlanGenerator generator = prompt -> "candidate-plan";
        PlanTextValidator validator = json -> null;
        PlanRepairResult out;
        try {
            out = new PlanRepairCoordinator(validator, 2).plan("send something", "", generator);
        } catch (RuntimeException escaped) {
            throw new AssertionError("missing plan validation verdict must not escape the repair boundary", escaped);
        }
        check(out.status() == PlanRepairResult.Status.NEEDS_CLARIFICATION,
                "missing validator verdict should yield a non-executable safe result");
        check(out.plan() == null, "missing validator verdict must never leak an unvalidated executable plan");
        check(out.attempts() == 1, "missing validator verdict should stop immediately");
        String explanation = out.clarification().toLowerCase();
        check(explanation.contains("couldn't") || explanation.contains("could not"),
                "missing validator verdict should truthfully say safe planning could not complete");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}