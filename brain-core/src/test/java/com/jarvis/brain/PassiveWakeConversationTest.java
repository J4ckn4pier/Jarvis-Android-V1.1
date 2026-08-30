package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class PassiveWakeConversationTest {
    private static int checks;

    public static void main(String[] args) {
        ambientSpeechIsIgnoredUntilWakeSignal();
        highConfidenceJarvisWakeOpensContinuedConversation();
        falseWakePhraseDoesNotActivate();
        lowConfidenceWakeDoesNotActivate();
        explicitSleepClosesPassiveSession();
        System.out.println("PassiveWakeConversationTest: " + checks + " assertions passed");
    }

    private static BrainEngine newBrain() {
        return BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-28T01:45:00Z"), ZoneOffset.UTC));
    }

    private static void ambientSpeechIsIgnoredUntilWakeSignal() {
        BrainEngine brain = newBrain();
        BrainResponse before = brain.handle("how are you");
        check(before.kind() == BrainResponse.Kind.IGNORED_AMBIENT, "ambient speech before wake must not enter conversation");
    }

    private static void highConfidenceJarvisWakeOpensContinuedConversation() {
        BrainEngine brain = newBrain();
        PassiveWakeCoordinator wake = new PassiveWakeCoordinator(brain, 0.78);
        check(wake.onWakeCandidate("hey jarvis", 0.93), "high-confidence Jarvis wake phrase should activate brain");
        BrainResponse first = brain.handle("how are you");
        check(first.kind() == BrainResponse.Kind.CONVERSATION, "first turn after passive wake should work without repeating Jarvis");
        check(first.acceptedWithoutWakeWord(), "continued-conversation turn should be marked as accepted without repeated wake word");
        BrainResponse followup = brain.handle("tell me more");
        check(followup.kind() == BrainResponse.Kind.CONVERSATION, "subsequent turns should remain conversational without repeated wake word");
    }

    private static void falseWakePhraseDoesNotActivate() {
        BrainEngine brain = newBrain();
        PassiveWakeCoordinator wake = new PassiveWakeCoordinator(brain, 0.78);
        check(!wake.onWakeCandidate("hey google", 0.99), "non-Jarvis phrase must not activate even at high confidence");
        check(brain.handle("how are you").kind() == BrainResponse.Kind.IGNORED_AMBIENT, "false wake must leave brain asleep");
    }

    private static void lowConfidenceWakeDoesNotActivate() {
        BrainEngine brain = newBrain();
        PassiveWakeCoordinator wake = new PassiveWakeCoordinator(brain, 0.78);
        check(!wake.onWakeCandidate("jarvis", 0.52), "low-confidence wake candidate should be rejected");
        check(brain.handle("how are you").kind() == BrainResponse.Kind.IGNORED_AMBIENT, "rejected wake must leave ambient speech ignored");
    }

    private static void explicitSleepClosesPassiveSession() {
        BrainEngine brain = newBrain();
        PassiveWakeCoordinator wake = new PassiveWakeCoordinator(brain, 0.78);
        wake.onWakeCandidate("jarvis", 0.95);
        BrainResponse sleep = brain.handle("stop listening");
        check(!sleep.sessionActive(), "explicit sleep should close continued-conversation session");
        check(brain.handle("how are you").kind() == BrainResponse.Kind.IGNORED_AMBIENT, "speech after explicit sleep should return to ambient-ignore mode");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
