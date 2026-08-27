package com.jarvis.brain;

public final class AttentionGate {
    private final double threshold;
    public AttentionGate(double threshold) { this.threshold = threshold; }
    public boolean shouldSurface(PredictionCandidate candidate) { return candidate.score() >= threshold; }
}
