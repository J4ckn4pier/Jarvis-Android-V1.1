package com.jarvis.brain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Owns pending acted-on recommendation/action episodes and converts later contextual signals into
 * one-shot proactive follow-ups. The coordinator never collects presence/location data itself;
 * privacy-sensitive signals are accepted only when the caller supplies explicit opt-in.
 */
public final class OutcomeFollowupCoordinator {
    private record PendingEpisode(RecommendationEpisode episode, Instant actedAt) {}

    private final EpisodeFollowupPolicy followupPolicy;
    private final ProactiveExecutive proactiveExecutive;
    private final Map<String, PendingEpisode> pending = new LinkedHashMap<>();

    public OutcomeFollowupCoordinator(EpisodeFollowupPolicy followupPolicy,
                                      ProactiveExecutive proactiveExecutive) {
        if (followupPolicy == null) throw new IllegalArgumentException("followup policy required");
        if (proactiveExecutive == null) throw new IllegalArgumentException("proactive executive required");
        this.followupPolicy = followupPolicy;
        this.proactiveExecutive = proactiveExecutive;
    }

    /** Registers an episode without assuming the user acted on it. */
    public synchronized void recordEpisode(RecommendationEpisode episode) {
        if (episode == null) throw new IllegalArgumentException("episode required");
        pending.remove(episode.id());
    }

    /** Marks an episode as acted on and therefore eligible for a later outcome follow-up. */
    public synchronized void recordActedOn(RecommendationEpisode episode, Instant actedAt) {
        if (episode == null) throw new IllegalArgumentException("episode required");
        Instant when = actedAt == null ? episode.recommendedAt() : actedAt;
        pending.put(episode.id(), new PendingEpisode(episode, when));
    }

    /** Number of acted-on episodes still awaiting a useful follow-up opportunity. */
    public synchronized int pendingCount() {
        return pending.size();
    }

    /**
     * Evaluates a later contextual signal. A rejected privacy signal or a silent policy decision
     * leaves the episode pending so a safer/later signal can still surface it. Once an intervention
     * is actually surfaced (SPEAK or NOTIFY), the episode is consumed to prevent repeated nagging.
     */
    public synchronized Optional<ProactiveIntervention> onSignal(String episodeId,
                                                                  FollowupTrigger trigger,
                                                                  boolean privacySensitiveTriggerOptIn,
                                                                  AttentionController.State attentionState,
                                                                  Instant now) {
        String id = episodeId == null ? "" : episodeId.trim();
        if (id.isBlank() || trigger == null || now == null) return Optional.empty();
        PendingEpisode pendingEpisode = pending.get(id);
        if (pendingEpisode == null) return Optional.empty();

        boolean presenceSignal = trigger == FollowupTrigger.USER_RETURNED_HOME;
        EpisodeFollowupPolicy.Trigger policyTrigger = new EpisodeFollowupPolicy.Trigger(
                true,
                presenceSignal,
                privacySensitiveTriggerOptIn,
                pendingEpisode.actedAt());
        Optional<PredictionCandidate> candidate = followupPolicy.candidate(
                pendingEpisode.episode(), policyTrigger, now);
        if (candidate.isEmpty()) return Optional.empty();

        ProactiveIntervention intervention = proactiveExecutive.decide(candidate.get(), attentionState, now);
        if (intervention.mode() == InterventionMode.SILENT) return Optional.empty();

        pending.remove(id);
        return Optional.of(intervention);
    }
}
