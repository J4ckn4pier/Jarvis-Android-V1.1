package com.jarvis.brain;

import java.util.List;

/** Persistence boundary for durable rich memory. Storage implementations own serialization/encryption. */
public interface LongTermMemoryPersistence {
    List<RichMemory> load();
    void save(List<RichMemory> memories);

    static LongTermMemoryPersistence none() {
        return new LongTermMemoryPersistence() {
            @Override public List<RichMemory> load() { return List.of(); }
            @Override public void save(List<RichMemory> memories) { }
        };
    }
}
