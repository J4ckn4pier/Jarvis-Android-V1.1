package com.jarvis.brain;

import java.util.Locale;

/**
 * Brain-side boundary for a low-power keyword spotter. The audio engine supplies phrase + confidence;
 * this class owns passive session admission only. Active-session interruption/barge-in remains in AttentionController.
 */
public final class PassiveWakeCoordinator {
    private final BrainEngine brain;
    private final AttentionController attention;
    private final double threshold;

    public PassiveWakeCoordinator(BrainEngine brain, double threshold) {
        this(brain, null, threshold);
    }

    public PassiveWakeCoordinator(BrainEngine brain, AttentionController attention, double threshold) {
        if (brain == null) throw new IllegalArgumentException("brain required");
        this.brain = brain;
        this.attention = attention;
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    public boolean onWakeCandidate(String phrase, double confidence) {
        String normalized = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        boolean phraseMatches = normalized.equals("jarvis") || normalized.equals("hey jarvis");
        if (!phraseMatches || confidence < threshold) return false;

        if (attention != null) {
            AttentionController.State state = attention.state();
            // Do not let the low-power wake detector compete with active conversation or AEC-gated barge-in.
            if (state == AttentionController.State.SPEAKING
                    || state == AttentionController.State.THINKING
                    || state == AttentionController.State.LISTENING) return false;
        }

        brain.handle("Jarvis");
        if (attention != null) attention.onWakeDetected();
        return true;
    }
}
