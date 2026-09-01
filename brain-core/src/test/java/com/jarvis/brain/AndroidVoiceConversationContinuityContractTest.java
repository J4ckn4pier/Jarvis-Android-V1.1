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
        check(session.contains("@Override public void onDestroy() {\n        destroyed = true;\n        sessionGeneration++;\n        recognitionGeneration++;"),
                "destroying the Assistant session must terminally mark the session and invalidate active recognizer callbacks before the recognizer is torn down");
        check(session.contains("catch (RuntimeException recognitionFailure)")
                        && session.contains("recoverRecognitionStartFailure(recognitionFailure);"),
                "Samsung/OEM recognizer creation or start exceptions must recover instead of crashing the active Assistant session");
        check(session.contains("private void recoverRecognitionStartFailure(RuntimeException recognitionFailure)")
                        && session.contains("recognitionGeneration++;")
                        && session.contains("speechRecognizer = null;")
                        && session.contains("scheduleNextListen();"),
                "recognizer-start recovery must invalidate callbacks, discard the failed recognizer, and reopen listening while the conversation remains active");
        check(session.contains("private boolean terminalDelivered;")
                        && session.contains("private boolean claimTerminal()"),
                "each active listening turn must latch its first terminal callback");
        check(occurrences(session, "if (!claimTerminal()) return;") >= 2,
                "both recognition error and final-result paths must reject duplicate terminal callbacks from the same Samsung/OEM listening turn");
        check(session.contains("ACTIVE_END_OF_SPEECH_TIMEOUT_MILLIS")
                        && session.contains("scheduleRecognitionTerminalWatchdog(listeningGeneration)")
                        && session.contains("invalidateRecognitionTerminalWatchdog()"),
                "active Assistant recognition must recover if Samsung/OEM sends end-of-speech but never sends results or an error");
        check(session.contains("private void handleRecognitionTerminalTimeout(long listeningGeneration)")
                        && session.contains("releaseSpeechRecognizerSafely();")
                        && session.contains("scheduleNextListen();"),
                "a stalled post-end-of-speech turn must discard the recognizer and reopen listening without executing stale speech");
        check(session.contains("ACTIVE_RECOGNIZER_READY_TIMEOUT_MILLIS")
                        && session.contains("scheduleRecognitionReadyWatchdog(listeningGeneration)")
                        && session.contains("invalidateRecognitionReadyWatchdog()"),
                "active Assistant recognition must recover if Samsung/OEM accepts startListening but never reports ready, results, or an error");
        check(session.contains("private void handleRecognitionReadyTimeout(long listeningGeneration)")
                        && session.contains("Active recognizer never became ready; rebuilding")
                        && session.contains("releaseSpeechRecognizerSafely();")
                        && session.contains("scheduleNextListen();"),
                "a pre-ready recognizer stall must discard the wedged recognizer and reopen listening without waiting forever");
        check(session.contains("private void releaseSpeechRecognizerSafely()")
                        && session.contains("try { recognizer.cancel(); } catch (RuntimeException cleanupFailure)")
                        && session.contains("try { recognizer.destroy(); } catch (RuntimeException cleanupFailure)"),
                "Samsung/OEM recognizer cleanup exceptions must be contained so hide/destroy lifecycle can finish normally");
        check(occurrences(session, "releaseSpeechRecognizerSafely();") >= 2,
                "both hide and destroy lifecycle paths must use guarded recognizer cleanup");
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
        check(session.contains("speechRecognizer.cancel()") || session.contains("recognizer.cancel()"), "hide/destroy lifecycle must cancel active recognition");

        System.out.println("AndroidVoiceConversationContinuityContractTest: PASS");
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
