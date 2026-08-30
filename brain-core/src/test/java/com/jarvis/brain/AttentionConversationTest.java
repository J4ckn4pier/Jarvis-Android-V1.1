package com.jarvis.brain;

public final class AttentionConversationTest {
    private static int passed;

    public static void main(String[] args) {
        wakeMovesSleepingAssistantIntoListening();
        responseCycleReturnsToOpenIdleForContinuedConversation();
        bargeInWorksOnlyWhenEchoCancellationIsAvailable();
        explicitDismissReturnsToSleeping();
        endpointingWaitsOnIncompleteThoughts();
        endpointingCommitsCompleteThoughtsWithoutSpeedTalking();
        longSilenceEventuallyCommitsEvenIncompleteSpeech();
        System.out.println("PASS " + passed + " attention/conversation assertions");
    }

    private static void wakeMovesSleepingAssistantIntoListening() {
        AttentionController c = new AttentionController(true);
        check(c.state() == AttentionController.State.SLEEPING, "assistant begins sleeping");
        c.onWakeDetected();
        check(c.state() == AttentionController.State.LISTENING, "wake detection should start listening");
    }

    private static void responseCycleReturnsToOpenIdleForContinuedConversation() {
        AttentionController c = new AttentionController(true);
        c.onWakeDetected();
        c.onSpeechCommitted();
        check(c.state() == AttentionController.State.THINKING, "committed speech should enter thinking");
        c.onResponseSpeaking();
        check(c.state() == AttentionController.State.SPEAKING, "response should enter speaking");
        c.onResponseFinished();
        check(c.state() == AttentionController.State.OPEN_IDLE, "after reply assistant should stay open for continued conversation");
        c.onSpeechStarted();
        check(c.state() == AttentionController.State.LISTENING, "follow-up speech should resume listening without another wake word");
    }

    private static void bargeInWorksOnlyWhenEchoCancellationIsAvailable() {
        AttentionController capable = new AttentionController(true);
        capable.onWakeDetected(); capable.onSpeechCommitted(); capable.onResponseSpeaking();
        capable.onSpeechStarted();
        check(capable.state() == AttentionController.State.LISTENING, "AEC-capable device should allow barge-in");

        AttentionController incapable = new AttentionController(false);
        incapable.onWakeDetected(); incapable.onSpeechCommitted(); incapable.onResponseSpeaking();
        incapable.onSpeechStarted();
        check(incapable.state() == AttentionController.State.SPEAKING, "without AEC, own TTS must not trigger barge-in");
    }

    private static void explicitDismissReturnsToSleeping() {
        AttentionController c = new AttentionController(true);
        c.onWakeDetected();
        c.dismiss();
        check(c.state() == AttentionController.State.SLEEPING, "dismiss should close conversation attention");
    }

    private static void endpointingWaitsOnIncompleteThoughts() {
        EndpointingPolicy p = new EndpointingPolicy();
        check(!p.shouldCommit("call mom and", 1100), "assistant should tolerate pause after conjunction");
        check(!p.shouldCommit("I need you to find", 1100), "assistant should tolerate pause on incomplete request");
    }

    private static void endpointingCommitsCompleteThoughtsWithoutSpeedTalking() {
        EndpointingPolicy p = new EndpointingPolicy();
        check(p.shouldCommit("how are you", 1100), "complete short utterance should commit after comfortable pause");
        check(p.shouldCommit("set a timer for twenty minutes", 1100), "complete command should commit after comfortable pause");
    }

    private static void longSilenceEventuallyCommitsEvenIncompleteSpeech() {
        EndpointingPolicy p = new EndpointingPolicy();
        check(p.shouldCommit("call mom and", 2500), "long silence should eventually commit rather than hang forever");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}
