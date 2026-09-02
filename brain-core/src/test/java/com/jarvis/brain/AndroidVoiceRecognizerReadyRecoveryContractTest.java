package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Samsung/OEM recognizers that start but never become ready must use bounded service recovery. */
public final class AndroidVoiceRecognizerReadyRecoveryContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));

        int start = session.indexOf("private void handleRecognitionReadyTimeout(long listeningGeneration)");
        int end = session.indexOf("\n    private ", start + 1);
        String body = start >= 0 ? session.substring(start, end > start ? end : session.length()) : "";

        check(body.contains("scheduleRecognitionServiceRecovery();"),
                "Samsung recognizer-ready watchdog timeout must enter bounded speech-service recovery");
        check(!body.contains("scheduleNextListen();"),
                "Samsung recognizer-ready watchdog timeout must not hammer the speech service through the generic 180ms relisten loop");
        check(body.contains("releaseSpeechRecognizerSafely();"),
                "a recognizer that never becomes ready must be retired before recovery");

        System.out.println("AndroidVoiceRecognizerReadyRecoveryContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
