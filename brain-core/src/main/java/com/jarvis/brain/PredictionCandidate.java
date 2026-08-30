package com.jarvis.brain;

public record PredictionCandidate(String message, double confidence, double urgency, double relevance,
                                  PredictionEvidenceTier evidenceTier, PredictionCategory category) {
    public PredictionCandidate {
        evidenceTier = evidenceTier == null ? PredictionEvidenceTier.INFERRED : evidenceTier;
        category = category == null ? PredictionCategory.GENERAL : category;
    }

    /** Legacy/basic predictions are conservative: inferred + general, therefore never proactive SPEAK. */
    public PredictionCandidate(String message, double confidence, double urgency, double relevance) {
        this(message, confidence, urgency, relevance, PredictionEvidenceTier.INFERRED, PredictionCategory.GENERAL);
    }

    public double score() {
        return 0.45 * clamp(confidence) + 0.30 * clamp(urgency) + 0.25 * clamp(relevance);
    }
    private static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }
}
