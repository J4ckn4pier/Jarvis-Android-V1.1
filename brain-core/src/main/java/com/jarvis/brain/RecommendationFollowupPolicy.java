package com.jarvis.brain;

import java.util.Optional;

/**
 * Converts episode-linked follow-up signals into proactive candidates.
 * Presence/location-derived signals are privacy-sensitive and require explicit opt-in.
 * This class does not collect location or presence data itself.
 */
public final class RecommendationFollowupPolicy {
    public Optional<PredictionCandidate> candidateFor(RecommendationEpisode episode,
                                                       FollowupTrigger trigger,
                                                       boolean privacySensitiveTriggerOptIn) {
        if (episode == null || trigger == null) return Optional.empty();
        if (trigger == FollowupTrigger.USER_RETURNED_HOME && !privacySensitiveTriggerOptIn) return Optional.empty();

        double urgency = trigger == FollowupTrigger.EXPLICIT_FOLLOWUP_REQUEST ? 0.99 : 0.90;
        String message = "How did my recommendation of " + episode.subject() + " work out?";
        return Optional.of(new PredictionCandidate(
                message,
                0.98,
                urgency,
                0.98,
                PredictionEvidenceTier.TRUSTED,
                PredictionCategory.RECOMMENDATION_FOLLOWUP));
    }
}
