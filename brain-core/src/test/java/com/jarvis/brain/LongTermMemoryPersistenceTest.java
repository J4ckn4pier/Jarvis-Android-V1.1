package com.jarvis.brain;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class LongTermMemoryPersistenceTest {
    public static void main(String[] args) {
        final class FakePersistence implements LongTermMemoryPersistence {
            List<RichMemory> saved = List.of();
            boolean failWrites;
            @Override public List<RichMemory> load() { return saved; }
            @Override public void save(List<RichMemory> memories) {
                if (failWrites) throw new IllegalStateException("storage unavailable");
                saved = List.copyOf(memories);
            }
        }

        Instant t1 = Instant.parse("2026-08-29T16:00:00Z");
        FakePersistence persistence = new FakePersistence();
        LongTermMemoryStore first = new LongTermMemoryStore(persistence);
        first.put(new RichMemory("preference.coffee", MemoryType.PREFERENCE, "Prefers dark roast coffee", "user-stated", 1.0, 0.8, t1, null, Set.of("coffee")));
        check(persistence.saved.size() == 1, "put must persist durable memory");

        LongTermMemoryStore restored = new LongTermMemoryStore(persistence);
        check(restored.current("preference.coffee", t1.plusSeconds(1)).isPresent(), "memory must survive store restart");
        check(restored.current("preference.coffee", t1.plusSeconds(1)).orElseThrow().content().contains("dark roast"), "restored memory content must be preserved");

        restored.archive("preference.coffee", t1.plusSeconds(2));
        LongTermMemoryStore archived = new LongTermMemoryStore(persistence);
        check(archived.current("preference.coffee", t1.plusSeconds(3)).isEmpty(), "archive must persist");
        check(!archived.history("preference.coffee").isEmpty(), "archive must preserve history");

        persistence.failWrites = true;
        archived.put(new RichMemory("goal.focus", MemoryType.GOAL, "Finish JARVIS", "manual-user-entry", 1.0, 1.0, t1.plusSeconds(4), null, Set.of("jarvis")));
        check(archived.current("goal.focus", t1.plusSeconds(5)).isPresent(), "storage failure must not erase truthful in-process memory");

        System.out.println("LongTermMemoryPersistenceTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
