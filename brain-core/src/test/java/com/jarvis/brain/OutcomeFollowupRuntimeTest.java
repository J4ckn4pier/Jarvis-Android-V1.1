package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;

/** Pins the platform-facing follow-up bridge to backend-owned, default-deny privacy settings. */
public final class OutcomeFollowupRuntimeTest {
    public static void main(String[] args) {
        SettingsStore settings = new SettingsStore();
        check(settings.snapshot().containsKey("presence_followup_opt_in"),
                "presence follow-up privacy choice must be an explicit backend setting, not absence-as-policy");
        check(!settings.bool("presence_followup_opt_in"),
                "privacy-sensitive presence follow-ups must default to opt-out");

        OutcomeFollowupCoordinator coordinator = new OutcomeFollowupCoordinator(
                new EpisodeFollowupPolicy(Duration.ZERO),
                new ProactiveExecutive(new AttentionGate(0.70), Duration.ZERO, true));
        OutcomeFollowupRuntime runtime = new OutcomeFollowupRuntime(settings, coordinator);
        Instant actedAt = Instant.parse("2026-08-29T12:00:00Z");
        RecommendationEpisode episode = new RecommendationEpisode(
                "episode-runtime-1", "errand", "pick up the repaired laptop", actedAt.minusSeconds(30));
        runtime.recordActedOn(episode, actedAt);

        check(runtime.onSignal(episode.id(), FollowupTrigger.USER_RETURNED_HOME,
                AttentionController.State.OPEN_IDLE, actedAt.plusSeconds(60)).isEmpty(),
                "platform caller must not be able to bypass default-deny presence privacy policy");
        check(coordinator.pendingCount() == 1,
                "privacy rejection must preserve the pending episode for a later safe signal");

        settings.put("presence_followup_opt_in", "true");
        ProactiveIntervention surfaced = runtime.onSignal(episode.id(), FollowupTrigger.USER_RETURNED_HOME,
                AttentionController.State.OPEN_IDLE, actedAt.plusSeconds(61)).orElseThrow();
        check(surfaced.mode() == InterventionMode.SPEAK,
                "explicit backend opt-in should allow trusted idle presence follow-up to reach SPEAK tier");

        RecommendationEpisode contextual = new RecommendationEpisode(
                "episode-runtime-2", "project", "the JARVIS provider migration", actedAt.minusSeconds(30));
        runtime.recordActedOn(contextual, actedAt);
        settings.put("presence_followup_opt_in", "false");
        check(runtime.onSignal(contextual.id(), FollowupTrigger.USER_REOPENED_RELATED_CONTEXT,
                AttentionController.State.OPEN_IDLE, actedAt.plusSeconds(90)).isPresent(),
                "non-presence contextual follow-up must not depend on location/presence opt-in");

        System.out.println("OutcomeFollowupRuntimeTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
