package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Converts subconscious predictions into conservative silent/notification/spoken interventions. */
public final class ProactiveExecutive {
    private final AttentionGate gate;
    private final Duration cooldown;
    private final boolean proactiveSpeakingEnabled;
    private final Map<String, Instant> lastSurfaced = new HashMap<>();

    /** Conservative default: proactive speaking is off until the user explicitly enables it. */
    public ProactiveExecutive(AttentionGate gate, Duration cooldown) {
        this(gate, cooldown, false);
    }

    public ProactiveExecutive(AttentionGate gate, Duration cooldown, boolean proactiveSpeakingEnabled) {
        this.gate = gate == null ? new AttentionGate(0.70) : gate;
        this.cooldown = cooldown == null || cooldown.isNegative() ? Duration.ZERO : cooldown;
        this.proactiveSpeakingEnabled = proactiveSpeakingEnabled;
    }

    public synchronized ProactiveIntervention decide(PredictionCandidate candidate,
                                                      AttentionController.State attentionState,
                                                      Instant now) {
        if (candidate == null || now == null) return ProactiveIntervention.silent(candidate, "missing candidate or time");
        if (!gate.shouldSurface(candidate)) return ProactiveIntervention.silent(candidate, "below attention threshold");

        String key = normalize(candidate.message());
        Instant last = lastSurfaced.get(key);
        if (last != null && now.isBefore(last.plus(cooldown))) return ProactiveIntervention.silent(candidate, "cooldown");

        AttentionController.State state = attentionState == null ? AttentionController.State.SLEEPING : attentionState;
        boolean userBusy = state == AttentionController.State.LISTENING
                || state == AttentionController.State.THINKING
                || state == AttentionController.State.SPEAKING;

        boolean trustedEnough = candidate.evidenceTier().maySpeakProactively();
        boolean categoryAllowed = candidate.category().timeCriticalSpeechAllowed();
        boolean scoreAllowed = candidate.urgency() >= 0.85 && candidate.confidence() >= 0.85 && candidate.relevance() >= 0.80;
        boolean maySpeak = proactiveSpeakingEnabled && !userBusy && trustedEnough && categoryAllowed && scoreAllowed;

        InterventionMode mode = maySpeak ? InterventionMode.SPEAK : InterventionMode.NOTIFY;
        lastSurfaced.put(key, now);
        String reason = maySpeak ? "opted-in trusted time-critical signal" :
                userBusy ? "user busy; notification only" :
                !proactiveSpeakingEnabled ? "proactive speaking disabled" :
                !trustedEnough ? "evidence not trusted enough for speech" :
                !categoryAllowed ? "category not permitted for proactive speech" :
                "useful but below proactive-speech threshold";
        return new ProactiveIntervention(mode, candidate, reason);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
