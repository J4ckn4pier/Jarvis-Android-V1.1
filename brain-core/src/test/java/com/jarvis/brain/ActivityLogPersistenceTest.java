package com.jarvis.brain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Proves the transparency/audit timeline survives restart without inventing success. */
public final class ActivityLogPersistenceTest {
    public static void main(String[] args) {
        MapPersistence persistence = new MapPersistence();
        ActivityRecord record = new ActivityRecord(
                "reservation-1",
                Instant.parse("2026-08-29T17:00:00Z"),
                "Reservation",
                ActivityRecord.Status.NEEDS_DECISION,
                "5:00 PM unavailable",
                Map.of("alternatives", "6:00 PM,6:45 PM", "source", "restaurant-web"));

        ActivityLog first = new ActivityLog(persistence);
        first.append(record);
        ActivityLog afterRestart = new ActivityLog(persistence);
        ActivityRecord restored = afterRestart.get("reservation-1").orElseThrow();
        check(restored.status() == ActivityRecord.Status.NEEDS_DECISION,
                "attention/failure state must survive restart");
        check(restored.at().equals(Instant.parse("2026-08-29T17:00:00Z")),
                "event timestamp must survive restart");
        check(restored.detail().equals("5:00 PM unavailable"),
                "audit detail must survive restart");
        check(restored.evidence().get("alternatives").contains("6:00 PM"),
                "audit evidence must survive restart");
        check(afterRestart.needsAttention().size() == 1,
                "restart must preserve unresolved activity attention state");

        check(afterRestart.remove("reservation-1"), "existing activity record must remove");
        check(new ActivityLog(persistence).get("reservation-1").isEmpty(),
                "removed audit record must not resurrect after restart");

        ActivityLogPersistence broken = new ActivityLogPersistence() {
            @Override public Map<String,ActivityRecord> load() { throw new IllegalStateException("unavailable"); }
            @Override public void put(ActivityRecord record) { throw new IllegalStateException("unavailable"); }
            @Override public void remove(String id) { throw new IllegalStateException("unavailable"); }
        };
        ActivityLog resilient = new ActivityLog(broken);
        resilient.append(record);
        check(resilient.get("reservation-1").isPresent(),
                "persistence failure must not erase truthful in-process audit state");
        resilient.remove("reservation-1");
        check(resilient.get("reservation-1").isEmpty(),
                "persistence failure must not block in-process user removal");

        System.out.println("ActivityLogPersistenceTest passed");
    }

    private static final class MapPersistence implements ActivityLogPersistence {
        private final Map<String,ActivityRecord> values = new LinkedHashMap<>();
        @Override public Map<String,ActivityRecord> load() { return Map.copyOf(values); }
        @Override public void put(ActivityRecord record) { values.put(record.id(), record); }
        @Override public void remove(String id) { values.remove(id); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
