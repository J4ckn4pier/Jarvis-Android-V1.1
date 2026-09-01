package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Old Android TTS callbacks and delayed re-listens must never disturb a newer utterance or listening turn. */
public final class AndroidVoiceTtsCallbackGenerationContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));

        check(session.contains("speechGeneration"),
                "voice session must generation-tag TTS playback so stale OEM callbacks can be rejected");
        check(session.contains("activeUtteranceId"),
                "voice session must track the currently authoritative TTS utterance");
        check(session.contains("isCurrentSpeechCallback(utteranceId)"),
                "TTS start/done/error callbacks must verify they belong to the active utterance");
        check(session.contains("invalidateSpeechCallback()"),
                "interrupt/hide/destroy paths must invalidate old TTS callbacks before stopping playback");
        check(!session.contains("textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, JARVIS_UTTERANCE)"),
                "every utterance must not reuse one static id because Android can deliver callbacks late");

        check(session.contains("listenScheduleGeneration"),
                "delayed continued-conversation re-listens must be generation-tagged");
        check(session.contains("invalidateScheduledListen()"),
                "new speech/listening/lifecycle transitions must be able to invalidate an older delayed re-listen");
        check(session.contains("scheduledGeneration != listenScheduleGeneration"),
                "a stale delayed re-listen must not reopen the microphone during a newer assistant turn");

        check(session.contains("private boolean destroyed;"),
                "voice session must remember terminal destruction so asynchronous TTS initialization cannot revive stale state");
        check(session.contains("@Override public void onInit(int status) {\n        if (destroyed) return;"),
                "a late Samsung/OEM TTS onInit callback after session destruction must be ignored");
        check(session.contains("private void releaseTextToSpeechSafely()")
                        && session.contains("textToSpeech = null;")
                        && session.contains("try { engine.stop(); } catch (RuntimeException cleanupFailure)")
                        && session.contains("try { engine.shutdown(); } catch (RuntimeException cleanupFailure)"),
                "TTS teardown must detach the engine first and contain OEM stop/shutdown exceptions independently");
        check(session.contains("destroyed = true;")
                        && session.contains("releaseTextToSpeechSafely();"),
                "session destruction must mark TTS callbacks stale before safely releasing the engine");
        check(session.contains("private boolean speakResponseSafely(String text, String utteranceId)")
                        && session.contains("catch (RuntimeException speechFailure)")
                        && session.contains("TextToSpeech.QUEUE_FLUSH"),
                "Samsung/OEM synchronous TTS speak failures must be contained behind a voice-session recovery boundary");
        check(session.contains("if (speakResponseSafely(text, utteranceId)) bargeInMonitor.start(this::handleHandsFreeBargeIn);")
                        && session.contains("invalidateSpeechCallback();")
                        && session.contains("scheduleNextListen();"),
                "failed TTS playback must skip barge-in, invalidate the utterance callback, and reopen listening while the conversation remains active");

        check(session.contains("private void stopTextToSpeechSafely()")
                        && session.contains("catch (RuntimeException stopFailure)"),
                "Samsung/OEM TTS stop failures during normal session transitions must be contained behind a safe stop boundary");
        check(!session.contains("if (textToSpeech != null) textToSpeech.stop();"),
                "hide, barge-in, speech-begin, and manual interrupt paths must never call OEM TTS stop directly");
        check(session.contains("private void applyVoicePreferences()")
                        && occurrences(session, "catch (RuntimeException preferenceFailure)") >= 2
                        && session.contains("TTS language preference failed")
                        && session.contains("TTS speech-rate preference failed"),
                "Samsung/OEM TTS language/rate configuration exceptions must be contained independently so Assistant show or TTS handoff cannot crash the voice session");

        System.out.println("AndroidVoiceTtsCallbackGenerationContractTest: PASS");
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
