package com.jarvis.brain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlanValidationTest {
    private static int passed;
    public static void main(String[] args) {
        standardRegistryCoversAssistantBenchmarkTools();
        validatorRejectsUnknownTools();
        validatorRejectsMissingRequiredArguments();
        validatorCannotDowngradeConsequentialTool();
        validatorRejectsNullPlanWithoutCrashing();
        validatorRejectsMissingStepListWithoutCrashing();
        validatorRejectsNullStepWithoutCrashing();
        validatorRejectsMissingArgumentsMapWithoutCrashing();
        System.out.println("PASS " + passed + " plan validation assertions");
    }

    private static void standardRegistryCoversAssistantBenchmarkTools() {
        ToolRegistry r = ToolRegistry.standard();
        for (String name : List.of("open_dialer", "discover_places", "rank_options", "weather_lookup", "set_timer", "create_reminder", "navigate", "media_play", "set_flashlight", "calendar_query", "notification_query", "translate", "send_message"))
            check(r.resolve(name).isPresent(), "standard registry must contain " + name);
    }

    private static void validatorRejectsUnknownTools() {
        PlanValidation result = new PlanValidator(ToolRegistry.standard()).validate(new Plan("bad", List.of(new PlanStep("invented_superpower"))));
        check(!result.valid(), "unknown tools must not pass validation");
    }

    private static void validatorRejectsMissingRequiredArguments() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("weather_lookup", false, Set.of(), Set.of("location", "when"), "weather"), (args, ctx) -> ToolResult.success("ok"));
        PlanValidation result = new PlanValidator(registry).validate(new Plan("weather", List.of(new PlanStep("weather_lookup", Map.of("when", "tomorrow"), false))));
        check(!result.valid(), "missing required tool arguments must fail validation");
        check(result.errors().stream().anyMatch(e -> e.contains("location")), "validation error should name missing argument");
    }

    private static void validatorCannotDowngradeConsequentialTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("send_message", true, Set.of(), Set.of("recipient", "message"), "send a message"), (args, ctx) -> ToolResult.success("sent"));
        Plan plan = new Plan("send", List.of(new PlanStep("send_message", Map.of("recipient", "Mom", "message", "Hi"), false)));
        PlanValidation result = new PlanValidator(registry).validate(plan);
        check(result.valid(), "consequential flag omission in proposed step should not invalidate otherwise safe plan");
        check(result.effectivePlan().requiresApproval(), "validator must elevate consequential tool to approval-required");
    }

    private static void validatorRejectsNullPlanWithoutCrashing() {
        PlanValidation result;
        try {
            result = new PlanValidator(ToolRegistry.standard()).validate(null);
        } catch (RuntimeException escaped) {
            throw new AssertionError("an absent provider plan must be a controlled validation failure", escaped);
        }
        check(!result.valid(), "null plan must not be executable");
        check(result.errors().stream().anyMatch(e -> e.toLowerCase().contains("plan")), "null-plan diagnostic should identify the malformed plan");
    }

    private static void validatorRejectsMissingStepListWithoutCrashing() {
        PlanValidation result;
        try {
            result = new PlanValidator(ToolRegistry.standard()).validate(new Plan("goal", null));
        } catch (RuntimeException escaped) {
            throw new AssertionError("provider plan with no step list must fail closed rather than crash validation", escaped);
        }
        check(!result.valid(), "plan with null steps must not be executable");
    }

    private static void validatorRejectsNullStepWithoutCrashing() {
        PlanValidation result;
        try {
            result = new PlanValidator(ToolRegistry.standard()).validate(new Plan("goal", Arrays.asList((PlanStep) null)));
        } catch (RuntimeException escaped) {
            throw new AssertionError("provider plan containing a null step must fail closed rather than crash validation", escaped);
        }
        check(!result.valid(), "plan containing a null step must not be executable");
    }

    private static void validatorRejectsMissingArgumentsMapWithoutCrashing() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("send_message", true, Set.of(), Set.of("recipient", "message"), "send"), (args, ctx) -> ToolResult.success("sent"));
        PlanValidation result;
        try {
            result = new PlanValidator(registry).validate(new Plan("send", List.of(new PlanStep("send_message", null, false))));
        } catch (RuntimeException escaped) {
            throw new AssertionError("provider step with no argument map must fail closed rather than crash validation", escaped);
        }
        check(!result.valid(), "step with null arguments must not be executable");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); passed++; }
}