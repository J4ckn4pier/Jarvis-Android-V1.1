package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;

public final class ProactiveSafetyPolicyTest {
    private static int checks;

    public static void main(String[] args) {
        speakingDefaultsOff();
        inferredEvidenceNeverSpeaks();
        generalPredictionNeverSpeaksEvenWhenTrusted();
        trustedTimeCriticalSignalMaySpeakWhenEnabledAndIdle();
        busyUserStillNeverGetsSpokenOver();
        System.out.println("ProactiveSafetyPolicyTest: " + checks + " assertions passed");
    }

    private static PredictionCandidate candidate(PredictionEvidenceTier tier, PredictionCategory category) {
        return new PredictionCandidate("Your appointment starts in one minute", 0.99, 0.99, 0.99, tier, category);
    }

    private static void speakingDefaultsOff() {
        ProactiveExecutive executive = new ProactiveExecutive(new AttentionGate(0.68), Duration.ofMinutes(30));
        ProactiveIntervention result = executive.decide(candidate(PredictionEvidenceTier.TRUSTED, PredictionCategory.IMMINENT_COMMITMENT),
                AttentionController.State.OPEN_IDLE, Instant.parse("2026-08-28T02:30:00Z"));
        check(result.mode() == InterventionMode.NOTIFY, "proactive speaking must default off while still allowing quiet notification");
    }

    private static void inferredEvidenceNeverSpeaks() {
        ProactiveExecutive executive = new ProactiveExecutive(new AttentionGate(0.68), Duration.ofMinutes(30), true);
        ProactiveIntervention result = executive.decide(candidate(PredictionEvidenceTier.INFERRED, PredictionCategory.IMMINENT_COMMITMENT),
                AttentionController.State.OPEN_IDLE, Instant.parse("2026-08-28T02:30:00Z"));
        check(result.mode() == InterventionMode.NOTIFY, "inferred-tier evidence must cap at NOTIFY");
    }

    private static void generalPredictionNeverSpeaksEvenWhenTrusted() {
        ProactiveExecutive executive = new ProactiveExecutive(new AttentionGate(0.68), Duration.ofMinutes(30), true);
        ProactiveIntervention result = executive.decide(candidate(PredictionEvidenceTier.TRUSTED, PredictionCategory.GENERAL),
                AttentionController.State.OPEN_IDLE, Instant.parse("2026-08-28T02:30:00Z"));
        check(result.mode() == InterventionMode.NOTIFY, "v1 proactive speech must be restricted to time-critical allow-list categories");
    }

    private static void trustedTimeCriticalSignalMaySpeakWhenEnabledAndIdle() {
        ProactiveExecutive executive = new ProactiveExecutive(new AttentionGate(0.68), Duration.ofMinutes(30), true);
        ProactiveIntervention result = executive.decide(candidate(PredictionEvidenceTier.TRUSTED, PredictionCategory.IMMINENT_COMMITMENT),
                AttentionController.State.OPEN_IDLE, Instant.parse("2026-08-28T02:30:00Z"));
        check(result.mode() == InterventionMode.SPEAK, "trusted imminent commitment may speak when user opted in and is idle");
    }

    private static void busyUserStillNeverGetsSpokenOver() {
        ProactiveExecutive executive = new ProactiveExecutive(new AttentionGate(0.68), Duration.ofMinutes(30), true);
        ProactiveIntervention result = executive.decide(candidate(PredictionEvidenceTier.TRUSTED, PredictionCategory.REMINDER),
                AttentionController.State.LISTENING, Instant.parse("2026-08-28T02:30:00Z"));
        check(result.mode() == InterventionMode.NOTIFY, "even trusted time-critical signals must not speak over LISTENING");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
