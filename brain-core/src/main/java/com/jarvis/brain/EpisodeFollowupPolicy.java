package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Creates a trusted episode-bound feedback prompt only after an opted-in completion signal. */
public final class EpisodeFollowupPolicy {
    public record Trigger(boolean outcomeActedOn, boolean presenceSignal, boolean presenceOptIn, Instant observedAt) {}
    private final Duration minimumDelay;
    public EpisodeFollowupPolicy(Duration minimumDelay){this.minimumDelay=minimumDelay==null||minimumDelay.isNegative()?Duration.ZERO:minimumDelay;}
    public Optional<PredictionCandidate> candidate(RecommendationEpisode episode, Trigger trigger, Instant now){
        if(episode==null||trigger==null||now==null||!trigger.outcomeActedOn())return Optional.empty();
        if(trigger.presenceSignal()&&!trigger.presenceOptIn())return Optional.empty();
        Instant observed=trigger.observedAt()==null?episode.recommendedAt():trigger.observedAt();
        if(now.isBefore(observed.plus(minimumDelay)))return Optional.empty();
        String message="How did "+episode.subject()+" work out?";
        return Optional.of(new PredictionCandidate(message,0.96,0.96,0.98,PredictionEvidenceTier.TRUSTED,PredictionCategory.RECOMMENDATION_FOLLOWUP));
    }
}
