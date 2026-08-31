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
        check(session.contains("getBoolean(\"voice_enabled\"")
                        && session.contains("getFloat(\"voice_rate\"")
                        && session.contains("getString(\"language\""),
                "voice-session overlay must honor the same user-facing voice and language settings as the full app");
        check(session.contains("isConversationEndCommand")
                        && session.contains("conversationDeadlineElapsedRealtime = 0L"),
                "spoken stop/sleep phrases must actually end continued-conversation listening");
        check(session.contains("Executors.newSingleThreadExecutor")
                        && session.contains("brainExecutor.execute(")
                        && session.contains("output.post(() -> {")
                        && session.contains("deliver(presentation);"),
                "voice brain/provider/tool work must run off Android's main thread and marshal presentation back to the UI thread");
        check(session.contains("catch (RuntimeException failure)")
                        && session.contains("AssistantSurfaceState.ERROR")
                        && session.contains("JARVIS_RUNTIME_FAILURE"),
                "unexpected voice provider/tool runtime failures must surface a truthful error instead of leaving the assistant stuck on Thinking");
        check(session.contains("sessionGeneration")
                        && session.contains("long submittedGeneration = sessionGeneration;")
                        && session.contains("submittedGeneration != sessionGeneration")
                        && session.contains("!sessionVisible"),
                "slow results from a hidden/previous voice session must not be delivered into a later visible conversation");
        check(session.contains("long recognitionGeneration;"),
                "active Assistant recognition must maintain its own callback generation");
        check(session.contains("long listeningGeneration = ++recognitionGeneration;"),
                "each active listening turn must bind callbacks to a new recognition generation");
        check(session.contains("listeningGeneration != recognitionGeneration || !sessionVisible"),
                "callbacks from a destroyed/replaced active-session recognizer must be ignored before they change UI, execute commands, or schedule listening");
        check(session.contains("@Override public void onHide() {\n        sessionGeneration++;\n        recognitionGeneration++;"),
                "hiding the Assistant session must invalidate the active recognizer generation before late OEM callbacks can arrive");
        check(session.contains("@Override public void onDestroy() {\n        sessionGeneration++;\n        recognitionGeneration++;"),
                "destroying the Assistant session must invalidate active recognizer callbacks before the recognizer is torn down");
        check(session.contains("brainExecutor.shutdownNow()"),
                "voice-session destruction must stop its background brain executor");
        check(approval.contains("runtime.hasPendingApproval()")
                        && approval.contains("isApproval(n)")
                        && approval.contains("MIN_VOICE_APPROVAL_CONFIDENCE")
                        && approval.contains("runtime.approvePending()"),
                "pending consequential actions must still require an explicit, confidence-gated recognized approval phrase before execution");
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
