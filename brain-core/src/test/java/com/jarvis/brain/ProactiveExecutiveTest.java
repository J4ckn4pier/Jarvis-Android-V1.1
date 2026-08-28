package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;

public final class ProactiveExecutiveTest {
    private static int checks;

    public static void main(String[] args) {
        lowValuePredictionStaysSubconscious();
        usefulPredictionCanNotifyWithoutInterrupting();
        urgentPredictionCanSpeakWhenIdle();
        normalSuggestionDoesNotInterruptConversation();
        criticalTimingMayInterruptConversation();
        duplicateSuggestionIsSuppressedDuringCooldown();
        System.out.println("ProactiveExecutiveTest: " + checks + " assertions passed");
    }

    private static ProactiveExecutive executive() {
        return new ProactiveExecutive(new AttentionGate(0.68), Duration.ofMinutes(30));
    }

    private static void lowValuePredictionStaysSubconscious() {
        ProactiveIntervention result = executive().decide(
                new PredictionCandidate("You may want to check something", 0.45, 0.20, 0.40),
                AttentionController.State.SLEEPING, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() == InterventionMode.SILENT, "low-value prediction should stay subconscious");
    }

    private static void usefulPredictionCanNotifyWithoutInterrupting() {
        ProactiveIntervention result = executive().decide(
                new PredictionCandidate("Your usual dinner-search time is approaching", 0.88, 0.35, 0.90),
                AttentionController.State.SLEEPING, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() == InterventionMode.NOTIFY, "useful non-urgent prediction should surface quietly");
    }

    private static void urgentPredictionCanSpeakWhenIdle() {
        ProactiveIntervention result = executive().decide(
                new PredictionCandidate("You need to leave in five minutes for your appointment", 0.96, 0.92, 0.96),
                AttentionController.State.OPEN_IDLE, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() == InterventionMode.SPEAK, "high-confidence urgent timing should be allowed to speak when idle");
    }

    private static void normalSuggestionDoesNotInterruptConversation() {
        ProactiveIntervention result = executive().decide(
                new PredictionCandidate("You normally start dinner around now", 0.92, 0.55, 0.93),
                AttentionController.State.LISTENING, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() != InterventionMode.SPEAK, "normal proactive suggestion must not talk over the user");
    }

    private static void criticalTimingMayInterruptConversation() {
        ProactiveIntervention result = executive().decide(
                new PredictionCandidate("Your scheduled meeting starts in one minute", 0.99, 0.99, 0.99),
                AttentionController.State.OPEN_IDLE, Instant.parse("2026-08-28T02:00:00Z"));
        check(result.mode() == InterventionMode.SPEAK, "extremely urgent high-confidence timing may interrupt idle continuation");
    }

    private static void duplicateSuggestionIsSuppressedDuringCooldown() {
        ProactiveExecutive executive = executive();
        PredictionCandidate candidate = new PredictionCandidate("You need to leave in five minutes", 0.96, 0.92, 0.96);
        Instant now = Instant.parse("2026-08-28T02:00:00Z");
        check(executive.decide(candidate, AttentionController.State.OPEN_IDLE, now).mode() == InterventionMode.SPEAK,
                "first urgent candidate should surface");
        check(executive.decide(candidate, AttentionController.State.OPEN_IDLE, now.plusSeconds(60)).mode() == InterventionMode.SILENT,
                "same candidate should be suppressed inside cooldown");
        check(executive.decide(candidate, AttentionController.State.OPEN_IDLE, now.plus(Duration.ofMinutes(31))).mode() == InterventionMode.SPEAK,
                "candidate may surface again after cooldown expires");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
