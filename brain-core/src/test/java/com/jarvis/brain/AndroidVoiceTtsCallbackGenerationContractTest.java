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
        check(session.contains("private void attachTtsProgressListenerSafely()")
                        && session.contains("catch (RuntimeException listenerFailure)")
                        && session.contains("TTS progress-listener attachment failed"),
                "Samsung/OEM failure while attaching the TTS progress listener must be contained so onInit cannot crash the Assistant session");
        check(session.contains("int listenerResult = engine.setOnUtteranceProgressListener(")
                        && session.contains("if (listenerResult == TextToSpeech.ERROR)")
                        && session.contains("TTS progress-listener attachment returned ERROR; retiring speech engine")
                        && session.contains("releaseTextToSpeechSafely();"),
                "TextToSpeech listener attachment can fail by returning ERROR without throwing; that engine must be retired so a callback-less speak cannot strand TTS-to-microphone handoff");
        check(session.contains("private void initializeTextToSpeechSafely()")
                        && session.contains("catch (RuntimeException initializationFailure)")
                        && session.contains("TTS initialization failed; continuing without spoken output"),
                "Samsung/OEM TTS construction failure must be contained so Assistant session creation still reaches listening");
        check(session.contains("brain = new AndroidBrainRuntime(getContext());\n        initializeTextToSpeechSafely();"),
                "onCreate must route OEM TTS construction through the safe boundary rather than constructing it inline");

        check(session.contains("TTS_TERMINAL_CALLBACK_TIMEOUT_MIN_MILLIS")
                        && session.contains("TTS_TERMINAL_CALLBACK_TIMEOUT_MAX_MILLIS"),
                "accepted Samsung/OEM TTS playback must have explicit bounded terminal-callback watchdog limits");
        check(session.contains("scheduleTtsTerminalWatchdog(utteranceId, text)"),
                "successful TTS submission must arm a text-aware watchdog in case Samsung starts speech but never sends onDone/onError");
        check(session.contains("private long ttsTerminalCallbackTimeoutMillis(String text)"),
                "Samsung TTS terminal recovery must estimate a bounded timeout from utterance length rather than strand a short conversational turn for a fixed minute");
        check(session.contains("TTS_TERMINAL_CALLBACK_TIMEOUT_MIN_MILLIS")
                        && session.contains("TTS_TERMINAL_CALLBACK_TIMEOUT_MAX_MILLIS")
                        && session.contains("Math.min(TTS_TERMINAL_CALLBACK_TIMEOUT_MAX_MILLIS")
                        && session.contains("Math.max(TTS_TERMINAL_CALLBACK_TIMEOUT_MIN_MILLIS"),
                "text-aware Samsung TTS terminal recovery must remain bounded by explicit minimum and maximum watchdog limits");
        check(session.contains("handleTtsTerminalTimeout(utteranceId)"),
                "a missing Samsung TTS terminal callback must enter an explicit recovery path");
        check(session.contains("invalidateTtsTerminalWatchdog()"),
                "normal TTS completion, interruption, hide, and destroy must be able to cancel a stale TTS watchdog");
        check(session.contains("TTS terminal callback timed out; reopening listening"),
                "watchdog expiry must be observable as a Samsung/OEM voice-session recovery event");

        check(session.contains("TTS_START_CALLBACK_TIMEOUT_MILLIS"),
                "accepted Samsung/OEM TTS playback must have a short bounded start-callback watchdog");
        check(session.contains("scheduleTtsStartWatchdog(utteranceId)"),
                "successful TTS submission must arm a start watchdog in case Samsung accepts speak but never begins audio");
        check(session.contains("handleTtsStartTimeout(utteranceId)"),
                "missing Samsung TTS onStart must enter an explicit recovery path instead of waiting for the long terminal timeout");
        check(session.contains("invalidateTtsStartWatchdog()"),
                "onStart, normal completion, interruption, hide, and destroy must invalidate any stale TTS start watchdog");
        check(session.contains("TTS start callback timed out; reopening listening"),
                "TTS start-watchdog expiry must be observable as a Samsung/OEM voice-session recovery event");
        check(session.contains("@Override public void onStart(String utteranceId) {\n                    if (!isCurrentSpeechCallback(utteranceId)) return;\n                    invalidateTtsStartWatchdog();"),
                "a real onStart callback must cancel the short start watchdog while preserving the terminal watchdog for completion");
        check(session.contains("@Override public void onStop(String utteranceId, boolean interrupted)")
                        && occurrences(session, "output.post(() -> finishSpeechCallback(utteranceId))") >= 3,
                "Samsung/OEM onStop must be treated as a terminal TTS event so an internally stopped utterance cannot strand conversational listening");
        check(!session.contains("recognognitionAvailable"),
                "voice-session source must keep the recognizer availability guard compilable while TTS recovery is hardened");

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
