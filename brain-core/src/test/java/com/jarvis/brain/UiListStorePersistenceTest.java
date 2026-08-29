package com.jarvis.brain;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Proves editable UI list state survives restart without sacrificing in-process truthfulness. */
public final class UiListStorePersistenceTest {
    public static void main(String[] args) {
        MapPersistence persistence = new MapPersistence();
        UiListStore first = new UiListStore(persistence);
        first.upsert(UiSection.TASKS, new UiListItem(
                "task-1",
                "Ship persistence",
                "Keep manual provenance",
                false,
                Map.of("provenance", "manual-user-edit", "priority", "high")));
        first.upsert(UiSection.PROJECTS, new UiListItem(
                "project-1",
                "JARVIS",
                "Brain-first",
                false,
                Map.of("provenance", "user-stated")));

        UiListStore afterRestart = new UiListStore(persistence);
        UiListItem restoredTask = afterRestart.get(UiSection.TASKS, "task-1").orElseThrow();
        check(restoredTask.title().equals("Ship persistence"), "title must survive restart");
        check(restoredTask.details().equals("Keep manual provenance"), "details must survive restart");
        check(restoredTask.attributes().get("provenance").equals("manual-user-edit"),
                "manual provenance must survive restart");
        check(afterRestart.get(UiSection.PROJECTS, "project-1").isPresent(),
                "section identity must survive restart");

        afterRestart.setCompleted(UiSection.TASKS, "task-1", true);
        UiListStore afterCompletionRestart = new UiListStore(persistence);
        check(afterCompletionRestart.get(UiSection.TASKS, "task-1").orElseThrow().completed(),
                "completion changes must persist");

        check(afterCompletionRestart.remove(UiSection.TASKS, "task-1"), "existing item must remove");
        UiListStore afterRemovalRestart = new UiListStore(persistence);
        check(afterRemovalRestart.get(UiSection.TASKS, "task-1").isEmpty(),
                "manual removal must persist rather than resurrect after restart");
        check(afterRemovalRestart.get(UiSection.PROJECTS, "project-1").isPresent(),
                "removing one item must preserve unrelated sections");

        UiListStorePersistence broken = new UiListStorePersistence() {
            @Override public Map<UiSection,Map<String,UiListItem>> load() {
                throw new IllegalStateException("unavailable");
            }
            @Override public void put(UiSection section, UiListItem item) {
                throw new IllegalStateException("unavailable");
            }
            @Override public void remove(UiSection section, String id) {
                throw new IllegalStateException("unavailable");
            }
        };
        UiListStore resilient = new UiListStore(broken);
        resilient.upsert(UiSection.SKILLS, new UiListItem("skill-1", "Local skill", "", false, Map.of()));
        check(resilient.get(UiSection.SKILLS, "skill-1").isPresent(),
                "persistence failure must not break in-process upsert state");
        resilient.setCompleted(UiSection.SKILLS, "skill-1", true);
        check(resilient.get(UiSection.SKILLS, "skill-1").orElseThrow().completed(),
                "persistence failure must not break in-process completion state");
        resilient.remove(UiSection.SKILLS, "skill-1");
        check(resilient.get(UiSection.SKILLS, "skill-1").isEmpty(),
                "persistence failure must not prevent in-process removal");

        System.out.println("UiListStorePersistenceTest passed");
    }

    private static final class MapPersistence implements UiListStorePersistence {
        private final Map<UiSection,Map<String,UiListItem>> values = new EnumMap<>(UiSection.class);

        @Override public Map<UiSection,Map<String,UiListItem>> load() {
            Map<UiSection,Map<String,UiListItem>> copy = new EnumMap<>(UiSection.class);
            for (Map.Entry<UiSection,Map<String,UiListItem>> entry : values.entrySet()) {
                copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
            }
            return copy;
        }

        @Override public void put(UiSection section, UiListItem item) {
            values.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(item.id(), item);
        }

        @Override public void remove(UiSection section, String id) {
            Map<String,UiListItem> items = values.get(section);
            if (items != null) items.remove(id);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
