package com.jarvis.brain;

import java.util.*;

/** In-memory semantics for editable/searchable list surfaces; persistence adapter comes later. */
public final class UiListStore {
    private final Map<UiSection, LinkedHashMap<String,UiListItem>> bySection = new EnumMap<>(UiSection.class);

    public synchronized void upsert(UiSection section, UiListItem item) {
        requireSection(section);
        if (item == null) throw new IllegalArgumentException("item required");
        bySection.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(item.id(), item);
    }

    public synchronized boolean remove(UiSection section, String id) {
        requireSection(section);
        LinkedHashMap<String,UiListItem> items = bySection.get(section);
        return items != null && items.remove(clean(id)) != null;
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

    private static String searchable(UiListItem item) {
        return (item.title() + " " + item.details() + " " + item.attributes()).toLowerCase(Locale.ROOT);
    }
    private static void requireSection(UiSection section) { if (section == null) throw new IllegalArgumentException("section required"); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
