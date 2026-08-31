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

        System.out.println("AndroidVoiceTtsCallbackGenerationContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
