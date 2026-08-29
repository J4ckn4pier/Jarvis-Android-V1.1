package com.jarvis.brain;

import java.time.Instant;

/**
 * Semantic platform-to-brain follow-up event. Raw location, geofence, or coordinate telemetry
 * must be resolved by the platform adapter before crossing this boundary.
 */
public record OutcomeFollowupSignal(
        String episodeId,
        FollowupTrigger trigger,
        AttentionController.State attentionState,
        Instant observedAt) {

    public OutcomeFollowupSignal {
        if (episodeId == null || episodeId.isBlank()) {
            throw new IllegalArgumentException("episodeId required");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("trigger required");
        }
        if (attentionState == null) {
            throw new IllegalArgumentException("attentionState required");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt required");
        }
    }
}
