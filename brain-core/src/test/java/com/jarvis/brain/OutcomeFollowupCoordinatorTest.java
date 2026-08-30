package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;

/** Proves acted-on episodes become privacy-safe, one-shot proactive follow-ups through the real policy gate. */
public final class OutcomeFollowupCoordinatorTest {
    public static void main(String[] args) {
        Instant actedAt = Instant.parse("2026-08-29T12:00:00Z");
        Instant later = actedAt.plus(Duration.ofHours(1));
        EpisodeFollowupPolicy policy = new EpisodeFollowupPolicy(Duration.ofMinutes(30));
        ProactiveExecutive proactive = new ProactiveExecutive(new AttentionGate(0.70), Duration.ZERO, true);
        OutcomeFollowupCoordinator coordinator = new OutcomeFollowupCoordinator(policy, proactive);

        RecommendationEpisode actionEpisode = new RecommendationEpisode(
                "episode-action-1", "workflow", "automated invoice reconciliation", actedAt.minusSeconds(60));
        coordinator.recordActedOn(actionEpisode, actedAt);
        check(coordinator.pendingCount() == 1, "acted-on episode must remain pending until a useful later signal");

        var intervention = coordinator.onSignal(
                actionEpisode.id(), FollowupTrigger.USER_REOPENED_RELATED_CONTEXT, false,
                AttentionController.State.OPEN_IDLE, later).orElseThrow();
        check(intervention.mode() == InterventionMode.SPEAK,
                "trusted episode follow-up must reach the opted-in proactive SPEAK tier while idle");
        check(intervention.candidate().message().contains("automated invoice reconciliation"),
                "follow-up must retain the originating episode subject");
        check(coordinator.pendingCount() == 0, "surfaced follow-up must be consumed exactly once");
        check(coordinator.onSignal(actionEpisode.id(), FollowupTrigger.USER_REOPENED_RELATED_CONTEXT, false,
                AttentionController.State.OPEN_IDLE, later.plusSeconds(60)).isEmpty(),
                "consumed episode must not nag repeatedly");

        RecommendationEpisode locationEpisode = new RecommendationEpisode(
                "episode-location-1", "dining", "the Italian restaurant", actedAt.minusSeconds(60));
        coordinator.recordActedOn(locationEpisode, actedAt);
        check(coordinator.onSignal(locationEpisode.id(), FollowupTrigger.USER_RETURNED_HOME, false,
                AttentionController.State.OPEN_IDLE, later).isEmpty(),
                "presence/location signal must fail closed without explicit opt-in");
        check(coordinator.pendingCount() == 1,
                "privacy-rejected signal must not discard the pending episode");
        check(coordinator.onSignal(locationEpisode.id(), FollowupTrigger.USER_RETURNED_HOME, true,
                AttentionController.State.OPEN_IDLE, later.plusSeconds(1)).orElseThrow().mode() == InterventionMode.SPEAK,
                "opted-in presence signal may surface the episode follow-up");

        RecommendationEpisode unacted = new RecommendationEpisode(
                "episode-unacted", "media", "Arrival", actedAt.minusSeconds(60));
        coordinator.recordEpisode(unacted);
        check(coordinator.onSignal(unacted.id(), FollowupTrigger.EXPLICIT_FOLLOWUP_REQUEST, true,
                AttentionController.State.OPEN_IDLE, later).isEmpty(),
                "episode that was never acted on must not create an outcome follow-up");

        System.out.println("OutcomeFollowupCoordinatorTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
