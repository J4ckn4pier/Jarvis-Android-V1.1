package com.jarvis.brain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Process-local store used when no durable platform store has been attached. */
public final class InMemoryOutcomeFollowupStore implements OutcomeFollowupStore {
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    @Override
    public synchronized List<Entry> loadAll() {
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    @Override
    public synchronized void upsert(Entry entry) {
        if (entry == null) throw new IllegalArgumentException("entry required");
        entries.put(entry.episode().id(), entry);
    }

    @Override
    public synchronized void remove(String episodeId) {
        if (episodeId == null) return;
        entries.remove(episodeId.trim());
    }
}
