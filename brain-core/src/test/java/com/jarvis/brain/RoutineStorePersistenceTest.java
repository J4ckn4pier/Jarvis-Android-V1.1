package com.jarvis.brain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Proves user-created routines and their executable plans survive restart safely. */
public final class RoutineStorePersistenceTest {
    public static void main(String[] args) {
        MapPersistence persistence = new MapPersistence();
        RoutineDefinition routine = new RoutineDefinition(
                "leave-work",
                "Leaving work",
                "phrase",
                Map.of("phrase", "I'm leaving work"),
                new Plan("Text Mom", List.of(new PlanStep(
                        "send_message",
                        Map.of("recipient", "Mom", "message", "I'm on my way"),
                        true))),
                true);

        RoutineStore first = new RoutineStore(persistence);
        first.upsert(routine);
        RoutineStore afterRestart = new RoutineStore(persistence);
        RoutineDefinition restored = afterRestart.get("leave-work").orElseThrow();
        check(restored.title().equals("Leaving work"), "routine title must survive restart");
        check(restored.triggerArguments().get("phrase").equals("I'm leaving work"),
                "trigger arguments must survive restart");
        check(restored.actionPlan().goal().equals("Text Mom"), "plan goal must survive restart");
        check(restored.actionPlan().steps().size() == 1, "plan steps must survive restart");
        check(restored.actionPlan().steps().get(0).consequential(),
                "consequential approval metadata must survive restart");

        afterRestart.setEnabled("leave-work", false);
        RoutineStore afterDisableRestart = new RoutineStore(persistence);
        check(!afterDisableRestart.get("leave-work").orElseThrow().enabled(),
                "enabled state changes must persist");
        check(afterDisableRestart.matching("phrase").isEmpty(),
                "disabled persisted routine must not become executable");

        check(afterDisableRestart.remove("leave-work"), "existing routine must remove");
        check(new RoutineStore(persistence).get("leave-work").isEmpty(),
                "manual removal must persist rather than resurrect after restart");

        RoutineStorePersistence broken = new RoutineStorePersistence() {
            @Override public Map<String,RoutineDefinition> load() { throw new IllegalStateException("unavailable"); }
            @Override public void put(RoutineDefinition routine) { throw new IllegalStateException("unavailable"); }
            @Override public void remove(String id) { throw new IllegalStateException("unavailable"); }
        };
        RoutineStore resilient = new RoutineStore(broken);
        resilient.upsert(routine);
        check(resilient.get("leave-work").isPresent(),
                "persistence failure must not break in-process routine state");
        resilient.setEnabled("leave-work", false);
        check(!resilient.get("leave-work").orElseThrow().enabled(),
                "persistence failure must not block in-process routine edits");
        resilient.remove("leave-work");
        check(resilient.get("leave-work").isEmpty(),
                "persistence failure must not block in-process routine removal");

        System.out.println("RoutineStorePersistenceTest passed");
    }

    private static final class MapPersistence implements RoutineStorePersistence {
        private final Map<String,RoutineDefinition> values = new LinkedHashMap<>();
        @Override public Map<String,RoutineDefinition> load() { return Map.copyOf(values); }
        @Override public void put(RoutineDefinition routine) { values.put(routine.id(), routine); }
        @Override public void remove(String id) { values.remove(id); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
