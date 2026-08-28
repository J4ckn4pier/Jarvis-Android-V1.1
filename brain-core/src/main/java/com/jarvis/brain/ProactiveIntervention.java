package com.jarvis.brain;

public record ProactiveIntervention(InterventionMode mode, PredictionCandidate candidate, String reason) {
    public ProactiveIntervention {
        mode = mode == null ? InterventionMode.SILENT : mode;
        reason = reason == null ? "" : reason;
    }

    public static ProactiveIntervention silent(PredictionCandidate candidate, String reason) {
        return new ProactiveIntervention(InterventionMode.SILENT, candidate, reason);
    }
}
