package com.jarvis.brain;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HabitLearner {
    private record Key(String action, DayOfWeek day, int hour) {}
    private static final class Stats {
        int count;
        final Map<String, Integer> contexts = new HashMap<>();
    }

    private final Map<Key, Stats> stats = new HashMap<>();

    public void observe(HabitEvent event) {
        Key key = new Key(event.action(), event.dayOfWeek(), event.hour());
        Stats s = stats.computeIfAbsent(key, k -> new Stats());
        s.count++;
        s.contexts.merge(event.context(), 1, Integer::sum);
    }

    public List<PredictionCandidate> predict(DayOfWeek day, int hour, String context) {
        List<PredictionCandidate> out = new ArrayList<>();
        for (Map.Entry<Key, Stats> entry : stats.entrySet()) {
            Key key = entry.getKey();
            Stats s = entry.getValue();
            if (key.day != day || key.hour != hour || s.count < 3) continue;
            int contextCount = s.contexts.getOrDefault(context == null ? "" : context, 0);
            double confidence = Math.min(0.98, 0.50 + (s.count - 2) * 0.08);
            double relevance = contextCount == 0 ? 0.35 : Math.min(0.98, 0.55 + 0.08 * contextCount);
            double urgency = 0.55;
            out.add(new PredictionCandidate("Likely next action: " + key.action, confidence, urgency, relevance));
        }
        out.sort(Comparator.comparingDouble(PredictionCandidate::score).reversed());
        return List.copyOf(out);
    }
}
