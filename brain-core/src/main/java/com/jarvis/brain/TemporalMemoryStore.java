package com.jarvis.brain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TemporalMemoryStore {
    private final Map<String,List<TemporalMemory>> byKey = new HashMap<>();
    public synchronized void remember(TemporalMemory memory) { byKey.computeIfAbsent(memory.key(), k -> new ArrayList<>()).add(memory); }
    public synchronized Optional<TemporalMemory> current(String key, Instant when) {
        return byKey.getOrDefault(key, List.of()).stream().filter(m -> m.validAt(when))
                .max(Comparator.comparing(TemporalMemory::validFrom).thenComparingDouble(TemporalMemory::confidence));
    }
    public synchronized List<TemporalMemory> history(String key) {
        List<TemporalMemory> list = new ArrayList<>(byKey.getOrDefault(key, List.of()));
        list.sort(Comparator.comparing(TemporalMemory::validFrom));
        return List.copyOf(list);
    }
}
