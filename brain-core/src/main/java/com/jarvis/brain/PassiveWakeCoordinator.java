package com.jarvis.brain;

import java.util.Locale;

/**
 * Brain-side boundary for a low-power keyword spotter. The audio engine supplies phrase + confidence;
 * this class decides whether the candidate is allowed to open JARVIS's continued-conversation session.
 */
public final class PassiveWakeCoordinator {
    private final BrainEngine brain;
    private final double threshold;

    public PassiveWakeCoordinator(BrainEngine brain, double threshold) {
        if (brain == null) throw new IllegalArgumentException("brain required");
        this.brain = brain;
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    public boolean onWakeCandidate(String phrase, double confidence) {
        String normalized = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        boolean phraseMatches = normalized.equals("jarvis") || normalized.equals("hey jarvis");
        if (!phraseMatches || confidence < threshold) return false;
        // Reuse the executive's canonical wake transition rather than duplicating session state here.
        brain.handle("Jarvis");
        return true;
    }
}
