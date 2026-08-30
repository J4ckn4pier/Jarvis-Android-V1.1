package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;

public final class ProactiveExecutiveTest {
    private static int checks;

    public static void main(String[] args) {
        lowValuePredictionStaysSubconscious();
        usefulPredictionCanNotifyWithoutInterrupting();
        urgentPredictionCanSpeakWhenIdleWhenOptedIn();
        normalSuggestionDoesNotInterruptConversation();
        criticalTimingMaySpeakWhenIdle();
        duplicateSuggestionIsSuppressedDuringCooldown();
        System.out.println("ProactiveExecutiveTest: " + checks + " assertions passed");
    }

    private static ProactiveExecutive executive(boolean speakingEnabled) {
        return new ProactiveExecutive(new AttentionGate(0.68), Duration.ofMinutes(30), speakingEnabled);
    }
    private static PredictionCandidate trustedUrgent(String message, PredictionCategory category) {
        return new PredictionCandidate(message, 0.96, 0.92, 0.96, PredictionEvidenceTier.TRUSTED, category);
    }

    private static void lowValuePredictionStaysSubconscious() {
        ProactiveIntervention result = executive(false).decide(
                new PredictionCandidate("You may want to check something", 0.45, 0.20, 0.40),
                AttentionController.State.SLEEPING, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() == InterventionMode.SILENT, "low-value prediction should stay subconscious");
    }
    private static void usefulPredictionCanNotifyWithoutInterrupting() {
        ProactiveIntervention result = executive(false).decide(
                new PredictionCandidate("Your usual dinner-search time is approaching", 0.88, 0.35, 0.90),
                AttentionController.State.SLEEPING, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() == InterventionMode.NOTIFY, "useful non-urgent prediction should surface quietly");
    }
    private static void urgentPredictionCanSpeakWhenIdleWhenOptedIn() {
        ProactiveIntervention result = executive(true).decide(
                trustedUrgent("You need to leave in five minutes for your appointment", PredictionCategory.IMMINENT_COMMITMENT),
                AttentionController.State.OPEN_IDLE, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() == InterventionMode.SPEAK, "trusted urgent timing may speak when opted in and idle");
    }
    private static void normalSuggestionDoesNotInterruptConversation() {
        ProactiveIntervention result = executive(true).decide(
                new PredictionCandidate("You normally start dinner around now", 0.92, 0.55, 0.93,
                        PredictionEvidenceTier.TRUSTED, PredictionCategory.GENERAL),
                AttentionController.State.LISTENING, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() != InterventionMode.SPEAK, "normal proactive suggestion must not talk over the user");
    }
    private static void criticalTimingMaySpeakWhenIdle() {
        ProactiveIntervention result = executive(true).decide(
                new PredictionCandidate("Your scheduled meeting starts in one minute", 0.99, 0.99, 0.99,
                        PredictionEvidenceTier.TRUSTED, PredictionCategory.CALENDAR_CONFLICT),
                AttentionController.State.OPEN_IDLE, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() == InterventionMode.SPEAK, "trusted explicit critical timing may speak when opted in and idle");
    }
    private static void duplicateSuggestionIsSuppressedDuringCooldown() {
        ProactiveExecutive executive = executive(true);
        PredictionCandidate candidate = trustedUrgent("You need to leave in five minutes", PredictionCategory.IMMINENT_COMMITMENT);
        Instant now = Instant.parse("2026-08-28T02:00:00Z");
        check(executive.decide(candidate, AttentionController.State.OPEN_IDLE, now).mode() == InterventionMode.SPEAK,
                "first trusted urgent candidate should surface");
        check(executive.decide(candidate, AttentionController.State.OPEN_IDLE, now.plusSeconds(60)).mode() == InterventionMode.SILENT,
                "same candidate should be suppressed inside cooldown");
        check(executive.decide(candidate, AttentionController.State.OPEN_IDLE, now.plus(Duration.ofMinutes(31))).mode() == InterventionMode.SPEAK,
                "candidate may surface again after cooldown expires");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
