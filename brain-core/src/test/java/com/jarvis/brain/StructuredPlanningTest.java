package com.jarvis.brain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class StructuredPlanningTest {
    private static int passed;

    public static void main(String[] args) {
        planCodecRoundTripsTypedSteps();
        plannerRejectsUnknownToolFromModel();
        plannerRejectsMissingRequiredArgumentsFromModel();
        plannerElevatesConsequentialToolApproval();
        contextAssemblerIncludesOnlyAvailableSignals();
        temporalMemoryPrefersCurrentlyValidFact();
        temporalMemoryCanRetainHistoryWithoutUsingExpiredFactAsCurrent();
        System.out.println("PASS " + passed + " structured planning/context assertions");
    }

    private static void planCodecRoundTripsTypedSteps() {
        Plan original = new Plan("find dinner", List.of(
                new PlanStep("discover_places", Map.of("category", "restaurant", "meal", "dinner"), false),
                new PlanStep("rank_options", Map.of("use_personal_context", "true"), false)
        ));
        String encoded = PlanJsonCodec.encode(original);
        Plan decoded = PlanJsonCodec.decode(encoded);
        check(decoded.goal().equals("find dinner"), "goal should round-trip");
        check(decoded.steps().size() == 2, "step count should round-trip");
        check(decoded.steps().get(0).arguments().get("category").equals("restaurant"), "arguments should round-trip");
    }

    private static void plannerRejectsUnknownToolFromModel() {
        ToolRegistry registry = ToolRegistry.standard();
        StructuredPlanner planner = new StructuredPlanner(registry);
        Plan proposed = new Plan("magic", List.of(new PlanStep("teleport_user", Map.of(), false)));
        PlanValidation result = planner.validateModelPlan(PlanJsonCodec.encode(proposed));
        check(!result.valid(), "model must not invent executable tools");
    }

    private static void plannerRejectsMissingRequiredArgumentsFromModel() {
        ToolRegistry registry = ToolRegistry.standard();
        StructuredPlanner planner = new StructuredPlanner(registry);
        Plan proposed = new Plan("text mom", List.of(new PlanStep("send_message", Map.of("recipient", "Mom"), false)));
        PlanValidation result = planner.validateModelPlan(PlanJsonCodec.encode(proposed));
        check(!result.valid(), "model plan missing message body must fail validation");
    }

    private static void plannerElevatesConsequentialToolApproval() {
        ToolRegistry registry = ToolRegistry.standard();
        StructuredPlanner planner = new StructuredPlanner(registry);
        Plan proposed = new Plan("text mom", List.of(new PlanStep("send_message", Map.of("recipient", "Mom", "message", "Running late"), false)));
        PlanValidation result = planner.validateModelPlan(PlanJsonCodec.encode(proposed));
        check(result.valid(), "valid message plan should validate");
        check(result.effectivePlan().requiresApproval(), "tool policy must override model attempt to omit approval flag");
    }

    private static void contextAssemblerIncludesOnlyAvailableSignals() {
        ContextAssembler assembler = new ContextAssembler();
        ContextSnapshot snapshot = assembler.assemble(new ContextSignals(
                "Maps", "Directions to downtown", "2026-08-27T18:00:00-06:00",
                "Castle Rock, CO", List.of("Dinner with Alex at 7 PM"),
                List.of("Mom: call me when free"), List.of("prefers Italian food")
        ));
        String prompt = snapshot.toPromptText();
        check(prompt.contains("Maps"), "current app should be included");
        check(prompt.contains("Castle Rock"), "location should be included when available");
        check(prompt.contains("Dinner with Alex"), "calendar should be included");
        check(prompt.contains("prefers Italian"), "relevant memory should be included");
    }

    private static void temporalMemoryPrefersCurrentlyValidFact() {
        TemporalMemoryStore store = new TemporalMemoryStore();
        store.remember(new TemporalMemory("home.city", "Denver", "user", 0.9,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-06-01T00:00:00Z")));
        store.remember(new TemporalMemory("home.city", "Castle Rock", "user", 0.98,
                Instant.parse("2026-06-01T00:00:00Z"), null));
        TemporalMemory current = store.current("home.city", Instant.parse("2026-08-27T00:00:00Z")).orElseThrow();
        check(current.value().equals("Castle Rock"), "current temporal fact should supersede expired history");
    }

    private static void temporalMemoryCanRetainHistoryWithoutUsingExpiredFactAsCurrent() {
        TemporalMemoryStore store = new TemporalMemoryStore();
        store.remember(new TemporalMemory("job", "Old Job", "user", 0.95,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z")));
        check(store.history("job").size() == 1, "expired memory should remain in history");
        check(store.current("job", Instant.parse("2026-08-27T00:00:00Z")).isEmpty(), "expired memory must not leak into current context");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}
