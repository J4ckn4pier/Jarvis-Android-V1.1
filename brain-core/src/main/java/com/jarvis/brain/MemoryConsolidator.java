package com.jarvis.brain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MemoryConsolidator {
    private record BehaviorKey(String behavior, String context) {}
    private static final class Evidence { int count; Instant first; }
    private final MemoryExtractor extractor;
    private final LongTermMemoryStore store;
    private final Map<BehaviorKey, Evidence> behaviorEvidence = new HashMap<>();

    public MemoryConsolidator(MemoryExtractor extractor, LongTermMemoryStore store) { this.extractor = extractor; this.store = store; }
    public LongTermMemoryStore store() { return store; }

    public void ingestUserTurn(String userTurn, Instant at) {
        for (ExtractedMemory extracted : extractor.extractUserStated(userTurn)) {
            store.put(new RichMemory(extracted.key(), extracted.type(), extracted.content(), "user-stated", 1.0, extracted.importance(), at, null, extracted.tags()));
        }
    }

    public void observeBehavior(String behavior, String context, Instant at) {
        BehaviorKey key = new BehaviorKey(normalize(behavior), normalize(context));
        Evidence evidence = behaviorEvidence.computeIfAbsent(key, k -> { Evidence e = new Evidence(); e.first = at; return e; });
        evidence.count++;
        String memoryKey = "routine." + normalize(behavior).replace(' ', '_');
        Set<String> routineTags = tags(behavior + " " + context);
        String content = "Usually " + behavior + " [context: " + context + "]";
        if (evidence.count == 3) {
            store.observeRoutine(memoryKey, content, routineTags, evidence.first);
            store.observeRoutine(memoryKey, content, routineTags, at);
            store.observeRoutine(memoryKey, content, routineTags, at);
        } else if (evidence.count > 3) {
            store.observeRoutine(memoryKey, content, routineTags, at);
        }
    }

    private static String normalize(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
    private static Set<String> tags(String s) { return java.util.Arrays.stream(normalize(s).split("\\s+")).filter(t -> t.length() >= 3).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
}
