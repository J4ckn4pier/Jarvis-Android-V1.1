package com.jarvis.brain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Team-review gates: structured pending plan must outlive dialogue eviction; retention never destroys history. */
public final class PlanStateAndRetentionTest {
    private static int checks;

    public static void main(String[] args) {
        pendingPlanSurvivesDialogueWindowEvictionAndRevalidates();
        retentionArchivesInsteadOfDeletingHistory();
        System.out.println("PlanStateAndRetentionTest: " + checks + " assertions passed");
    }

    private static void pendingPlanSurvivesDialogueWindowEvictionAndRevalidates() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T01:30:00Z"), ZoneOffset.UTC);
        ReasoningRouter router = request -> new ReasoningResult("planner", "Need destination.",
                new Plan("Navigate Castle dinner", List.of(new PlanStep("navigate", Map.of(), false))));
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), router, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        BrainResponse first = core.handle("take me to dinner");
        check(first.kind() == BrainResponse.Kind.CONVERSATION, "missing destination should create pending clarification");
        check(core.hasPendingPlan(), "pending plan must be explicit structured state");
        check(core.pendingPlanGoal().equals("Navigate Castle dinner"), "original goal must live outside dialogue buffer");

        // Deliberately overflow the 12-message dialogue buffer without satisfying the pending field.
        for (int i = 0; i < 20; i++) core.noteDialogueForTest("USER", "side conversation " + i);
        check(core.hasPendingPlan(), "dialogue eviction must not evict pending plan state");
        check(core.pendingPlanGoal().equals("Navigate Castle dinner"), "pending goal must remain intact after dialogue eviction");

        BrainResponse resumed = core.handle("Castle Cafe");
        check(resumed.kind() == BrainResponse.Kind.ACTION_PLAN, "clarification should resume the independent pending plan");
        check(resumed.plan().steps().get(0).arguments().get("destination").equals("Castle Cafe"),
                "clarification value must survive validator normalization");
        PlanValidation validation = new PlanValidator(ToolRegistry.standard()).validate(resumed.plan());
        check(validation.valid(), "resumed plan must pass the same PlanValidator path as provider plans");
    }

    private static void retentionArchivesInsteadOfDeletingHistory() {
        LongTermMemoryStore store = new LongTermMemoryStore();
        Instant now = Instant.parse("2026-08-28T01:30:00Z");
        store.put(new RichMemory("noise.old", MemoryType.EPISODE, "Saw a random blue car", "observed",
                0.7, 0.08, now.minus(Duration.ofDays(400)), null, Set.of("car")));
        MemoryRetentionPolicy policy = new MemoryRetentionPolicy(Duration.ofDays(120), 0.25, 0.80);
        int archived = store.prune(policy, now);
        check(archived == 1, "one stale low-value memory should leave the hot set");
        check(store.history("noise.old").size() == 1, "retention must preserve historical record instead of hard-deleting it");
        check(store.current("noise.old", now).isEmpty(), "archived memory must not remain current/hot");
        check(store.searchHistory("blue car", 5).size() == 1, "archived history must remain explicitly searchable");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
