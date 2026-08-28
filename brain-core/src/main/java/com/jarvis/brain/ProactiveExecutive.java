package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Converts subconscious predictions into silent/notification/spoken interventions. */
public final class ProactiveExecutive {
    private final AttentionGate gate;
    private final Duration cooldown;
    private final Map<String, Instant> lastSurfaced = new HashMap<>();

    public ProactiveExecutive(AttentionGate gate, Duration cooldown) {
        this.gate = gate == null ? new AttentionGate(0.70) : gate;
        this.cooldown = cooldown == null || cooldown.isNegative() ? Duration.ZERO : cooldown;
    }

    public synchronized ProactiveIntervention decide(PredictionCandidate candidate,
                                                      AttentionController.State attentionState,
                                                      Instant now) {
        if (candidate == null || now == null) return ProactiveIntervention.silent(candidate, "missing candidate or time");
        if (!gate.shouldSurface(candidate)) return ProactiveIntervention.silent(candidate, "below attention threshold");

        String key = normalize(candidate.message());
        Instant last = lastSurfaced.get(key);
        if (last != null && now.isBefore(last.plus(cooldown))) {
            return ProactiveIntervention.silent(candidate, "cooldown");
        }

        AttentionController.State state = attentionState == null ? AttentionController.State.SLEEPING : attentionState;
        boolean userBusy = state == AttentionController.State.LISTENING
                || state == AttentionController.State.THINKING
                || state == AttentionController.State.SPEAKING;

        InterventionMode mode;
        if (userBusy) {
            // Never speak over an active turn. Preserve the signal as a quiet notification instead.
            mode = InterventionMode.NOTIFY;
        } else if (candidate.urgency() >= 0.85 && candidate.confidence() >= 0.85 && candidate.relevance() >= 0.80) {
            mode = InterventionMode.SPEAK;
        } else {
            mode = InterventionMode.NOTIFY;
        }

        lastSurfaced.put(key, now);
        return new ProactiveIntervention(mode, candidate,
                mode == InterventionMode.SPEAK ? "urgent and high-confidence" : "useful but non-interruptive");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
