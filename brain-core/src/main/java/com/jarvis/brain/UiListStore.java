package com.jarvis.brain;

import java.util.*;

/** Editable/searchable list semantics with an optional durable persistence boundary. */
public final class UiListStore {
    private final Map<UiSection, LinkedHashMap<String,UiListItem>> bySection = new EnumMap<>(UiSection.class);
    private final UiListStorePersistence persistence;

    public UiListStore() {
        this(UiListStorePersistence.none());
    }

    public UiListStore(UiListStorePersistence persistence) {
        this.persistence = persistence == null ? UiListStorePersistence.none() : persistence;
        restore();
    }

    public synchronized void upsert(UiSection section, UiListItem item) {
        requireSection(section);
        if (item == null) throw new IllegalArgumentException("item required");
        bySection.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(item.id(), item);
        try {
            persistence.put(section, item);
        } catch (RuntimeException ignored) {
            // In-process state remains authoritative for this session when storage is unavailable.
        }
    }

    public synchronized boolean remove(UiSection section, String id) {
        requireSection(section);
        String cleanId = clean(id);
        LinkedHashMap<String,UiListItem> items = bySection.get(section);
        boolean removed = items != null && items.remove(cleanId) != null;
        if (removed) {
            try {
                persistence.remove(section, cleanId);
            } catch (RuntimeException ignored) {
                // Preserve truthful in-process removal even if durable storage is unavailable.
            }
        }
        return removed;
    }

    public synchronized Optional<UiListItem> get(UiSection section, String id) {
        requireSection(section);
        return Optional.ofNullable(bySection.getOrDefault(section, new LinkedHashMap<>()).get(clean(id)));
    }

    public synchronized List<UiListItem> list(UiSection section) {
        requireSection(section);
        return List.copyOf(bySection.getOrDefault(section, new LinkedHashMap<>()).values());
    }

    public synchronized List<UiListItem> search(UiSection section, String query) {
        requireSection(section);
        String q = clean(query).toLowerCase(Locale.ROOT);
        if (q.isBlank()) return list(section);
        return list(section).stream().filter(item -> searchable(item).contains(q)).toList();
    }

    public synchronized UiListItem setCompleted(UiSection section, String id, boolean completed) {
        UiListItem current = get(section, id).orElseThrow(() -> new IllegalArgumentException("unknown item: " + id));
        UiListItem updated = current.withCompleted(completed);
        upsert(section, updated);
        return updated;
    }

    private void restore() {
        Map<UiSection,Map<String,UiListItem>> restored;
        try {
            restored = persistence.load();
        } catch (RuntimeException ignored) {
            return;
        }
        if (restored == null) return;
        for (Map.Entry<UiSection,Map<String,UiListItem>> sectionEntry : restored.entrySet()) {
            UiSection section = sectionEntry.getKey();
            Map<String,UiListItem> items = sectionEntry.getValue();
            if (section == null || items == null) continue;
            LinkedHashMap<String,UiListItem> target = bySection.computeIfAbsent(section, ignored -> new LinkedHashMap<>());
            for (Map.Entry<String,UiListItem> itemEntry : items.entrySet()) {
                UiListItem item = itemEntry.getValue();
                if (item == null || item.id().isBlank()) continue;
                target.put(item.id(), item);
            }
        }
    }

    private static String searchable(UiListItem item) {
        return (item.title() + " " + item.details() + " " + item.attributes()).toLowerCase(Locale.ROOT);
    }
    private static void requireSection(UiSection section) { if (section == null) throw new IllegalArgumentException("section required"); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
