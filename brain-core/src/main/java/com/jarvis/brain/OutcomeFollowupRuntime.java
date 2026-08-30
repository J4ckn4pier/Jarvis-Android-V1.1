package com.jarvis.brain;

import java.time.Instant;
import java.util.Optional;

/**
 * Platform-facing bridge for outcome follow-ups. Privacy-sensitive trigger consent is read from
 * backend settings here so Android/event adapters cannot bypass policy by supplying their own flag.
 */
public final class OutcomeFollowupRuntime {
    public static final String PRESENCE_FOLLOWUP_OPT_IN = "presence_followup_opt_in";

    private final SettingsStore settings;
    private final OutcomeFollowupCoordinator coordinator;

    public OutcomeFollowupRuntime(SettingsStore settings, OutcomeFollowupCoordinator coordinator) {
        if (settings == null) throw new IllegalArgumentException("settings required");
        if (coordinator == null) throw new IllegalArgumentException("followup coordinator required");
        this.settings = settings;
        this.coordinator = coordinator;
    }

    public void recordEpisode(RecommendationEpisode episode) {
        coordinator.recordEpisode(episode);
    }

    public void recordActedOn(RecommendationEpisode episode, Instant actedAt) {
        coordinator.recordActedOn(episode, actedAt);
    }

    /** Preferred platform boundary: callers provide only a semantic event, never raw telemetry. */
    public Optional<ProactiveIntervention> onSignal(OutcomeFollowupSignal signal) {
        if (signal == null) throw new IllegalArgumentException("signal required");
        return onSignal(signal.episodeId(), signal.trigger(), signal.attentionState(), signal.observedAt());
    }

    /**
     * Compatibility entry point retained for existing callers. Consent is still resolved internally
     * and cannot be supplied by the platform caller.
     */
    public Optional<ProactiveIntervention> onSignal(String episodeId,
                                                     FollowupTrigger trigger,
                                                     AttentionController.State attentionState,
                                                     Instant now) {
        boolean presenceOptIn = settings.bool(PRESENCE_FOLLOWUP_OPT_IN);
        return coordinator.onSignal(episodeId, trigger, presenceOptIn, attentionState, now);
    }

    public int pendingCount() {
        return coordinator.pendingCount();
    }
}
