package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android assistant surface must continue listening across normal turns without repeated wake/tap. */
public final class AndroidVoiceConversationContinuityContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));

        check(session.contains("CONVERSATION_WINDOW_MILLIS"), "voice surface must define a bounded active conversation window");
        check(session.contains("10 * 60 * 1000L"), "beta conversation window must remain approximately ten minutes");
        check(session.contains("UtteranceProgressListener"), "voice surface must observe TTS completion before reopening recognition");
        check(session.contains("scheduleNextListen"), "normal assistant turns must schedule the next listen automatically");
        check(session.contains("conversationDeadlineElapsedRealtime"), "voice surface must track when the active conversation window ends");
        check(session.contains("presentation.state() == AssistantSurfaceState.AWAITING_APPROVAL"), "approval state must remain explicit");
        check(session.contains("presentation.state() == AssistantSurfaceState.NEEDS_INPUT"), "recovery state must remain explicit");
        check(session.contains("if (!approval && !recovery)"), "auto-listen must pause while approval/recovery input is pending");
        check(session.contains("@Override public void onHide()"), "hiding the assistant must stop continuous listening");
        check(session.contains("speechRecognizer.cancel()"), "hide/destroy lifecycle must cancel active recognition");

        System.out.println("AndroidVoiceConversationContinuityContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
