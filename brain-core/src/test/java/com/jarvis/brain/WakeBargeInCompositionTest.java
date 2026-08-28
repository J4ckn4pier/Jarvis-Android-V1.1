package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class WakeBargeInCompositionTest {
    private static int checks;

    public static void main(String[] args) {
        sleepingWakeOpensListeningExactlyOnce();
        wakeCandidateDuringSpeakingDoesNotDoubleActivate();
        aecBargeInOwnsSpeakingInterruption();
        System.out.println("WakeBargeInCompositionTest: " + checks + " assertions passed");
    }

    private static void sleepingWakeOpensListeningExactlyOnce() {
        BrainEngine brain = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T03:20:00Z"), ZoneOffset.UTC));
        AttentionController attention = new AttentionController(true);
        PassiveWakeCoordinator wake = new PassiveWakeCoordinator(brain, attention, 0.75);
        check(attention.state() == AttentionController.State.SLEEPING, "attention starts sleeping");
        check(wake.onWakeCandidate("hey jarvis", 0.95), "valid sleeping wake should activate");
        check(attention.state() == AttentionController.State.LISTENING, "wake coordinator should own sleeping->listening transition");
    }

    private static void wakeCandidateDuringSpeakingDoesNotDoubleActivate() {
        BrainEngine brain = BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T03:20:00Z"), ZoneOffset.UTC));
        AttentionController attention = speakingAttention(true);
        PassiveWakeCoordinator wake = new PassiveWakeCoordinator(brain, attention, 0.75);
        check(!wake.onWakeCandidate("hey jarvis", 0.99), "keyword hit during SPEAKING must not invoke wake path");
        check(attention.state() == AttentionController.State.SPEAKING, "wake detector must leave speaking state untouched");
    }

    private static void aecBargeInOwnsSpeakingInterruption() {
        AttentionController attention = speakingAttention(true);
        attention.onSpeechStarted();
        check(attention.state() == AttentionController.State.LISTENING,
                "AEC-gated speech start, not wake-word activation, should own barge-in during SPEAKING");

        AttentionController noAec = speakingAttention(false);
        noAec.onSpeechStarted();
        check(noAec.state() == AttentionController.State.SPEAKING,
                "without AEC, barge-in must stay disabled to avoid self-triggering on JARVIS TTS");
    }

    private static AttentionController speakingAttention(boolean aec) {
        AttentionController attention = new AttentionController(aec);
        attention.onWakeDetected();
        attention.onSpeechCommitted();
        attention.onResponseSpeaking();
        return attention;
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
