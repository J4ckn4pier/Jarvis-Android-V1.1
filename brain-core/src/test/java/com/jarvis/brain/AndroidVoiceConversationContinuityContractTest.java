package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android assistant surface must continue listening across normal and explicit approval/recovery turns without repeated wake/tap. */
public final class AndroidVoiceConversationContinuityContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));
        String approval = Files.readString(Path.of("src/main/java/com/jarvis/brain/RuntimeApprovalConversation.java"));

        check(session.contains("CONVERSATION_WINDOW_MILLIS"), "voice surface must define a bounded active conversation window");
        check(session.contains("10 * 60 * 1000L"), "beta conversation window must remain approximately ten minutes");
        check(session.contains("UtteranceProgressListener"), "voice surface must observe TTS completion before reopening recognition");
        check(session.contains("scheduleNextListen"), "assistant turns must schedule the next listen automatically");
        check(session.contains("conversationDeadlineElapsedRealtime"), "voice surface must track when the active conversation window ends");
        check(session.contains("presentation.state() == AssistantSurfaceState.AWAITING_APPROVAL"), "approval state must remain explicit");
        check(session.contains("presentation.state() == AssistantSurfaceState.NEEDS_INPUT"), "recovery state must remain explicit");
        check(!session.contains("if (!approval && !recovery)"), "approval/recovery prompts must not force a tap before the user can answer by voice");
        check(session.contains("resumeAfterSpeech = conversationWindowOpen();"), "spoken prompts must reopen recognition while the active conversation window remains open");
        check(approval.contains("if(runtime.hasPendingApproval())") && approval.contains("if(isApproval(n))return RuntimeSurfacePresentation.from(runtime.approvePending())"),
                "pending consequential actions must still require an explicit recognized approval phrase before execution");
        check(approval.contains("v.equals(\"yes\")") && approval.contains("v.equals(\"go ahead\")") && approval.contains("v.equals(\"confirm\")"),
                "voice approval vocabulary must remain explicit and narrow");
        check(approval.contains("if(isDeferral(n))return cancel"), "spoken deferral/cancel must remain available while an approval is pending");
        check(session.contains("@Override public void onHide()"), "hiding the assistant must stop continuous listening");
        check(session.contains("speechRecognizer.cancel()"), "hide/destroy lifecycle must cancel active recognition");

        System.out.println("AndroidVoiceConversationContinuityContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
