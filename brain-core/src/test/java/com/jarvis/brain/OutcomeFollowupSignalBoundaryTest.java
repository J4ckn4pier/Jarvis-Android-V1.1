package com.jarvis.brain;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/** Ensures platform event sources send semantic follow-up signals, never raw presence/location telemetry. */
public final class OutcomeFollowupSignalBoundaryTest {
    public static void main(String[] args) {
        check(OutcomeFollowupSignal.class.isRecord(), "follow-up signal must be an immutable typed event");
        String componentNames = Arrays.stream(OutcomeFollowupSignal.class.getRecordComponents())
                .map(RecordComponent::getName).map(String::toLowerCase).reduce("", (a,b) -> a + " " + b);
        check(!componentNames.contains("latitude") && !componentNames.contains("longitude")
                        && !componentNames.contains("coordinate") && !componentNames.contains("location")
                        && !componentNames.contains("geofence"),
                "brain event boundary must not accept raw location/geofence telemetry");

        SettingsStore settings = new SettingsStore();
        OutcomeFollowupCoordinator coordinator = new OutcomeFollowupCoordinator(
                new EpisodeFollowupPolicy(Duration.ZERO),
                new ProactiveExecutive(new AttentionGate(0.70), Duration.ZERO, true));
        OutcomeFollowupRuntime runtime = new OutcomeFollowupRuntime(settings, coordinator);
        Instant actedAt = Instant.parse("2026-08-29T12:00:00Z");
        RecommendationEpisode episode = new RecommendationEpisode(
                "episode-signal-1", "project", "the JARVIS migration", actedAt.minusSeconds(30));
        runtime.recordActedOn(episode, actedAt);

        OutcomeFollowupSignal presence = new OutcomeFollowupSignal(
                episode.id(), FollowupTrigger.USER_RETURNED_HOME,
                AttentionController.State.OPEN_IDLE, actedAt.plusSeconds(60));
        check(runtime.onSignal(presence).isEmpty(),
                "semantic returned-home signal must still fail closed until backend consent is enabled");
        settings.put(SettingsStore.PRESENCE_FOLLOWUP_OPT_IN, "true");
        check(runtime.onSignal(presence).isPresent(),
                "same semantic signal may surface only after explicit backend consent");

        System.out.println("OutcomeFollowupSignalBoundaryTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
