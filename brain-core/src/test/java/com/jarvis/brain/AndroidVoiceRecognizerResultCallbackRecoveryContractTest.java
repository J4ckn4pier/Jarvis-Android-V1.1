package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Samsung/OEM result Bundle/callback failures must retire the recognizer and use bounded service recovery. */
public final class AndroidVoiceRecognizerResultCallbackRecoveryContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));

        int start = session.indexOf("private void recoverRecognitionResultCallback(RuntimeException resultFailure)");
        int end = session.indexOf("\n    private ", start + 1);
        String body = start >= 0 ? session.substring(start, end > start ? end : session.length()) : "";

        check(body.contains("releaseSpeechRecognizerSafely();"),
                "a failed Samsung/OEM result callback must retire the active recognizer");
        check(body.contains("scheduleRecognitionServiceRecovery();"),
                "Samsung/OEM result callback failure must enter bounded speech-service recovery");
        check(!body.contains("scheduleNextListen();"),
                "Samsung/OEM result callback failure must not hammer the service through the generic 180ms relisten loop");

        System.out.println("AndroidVoiceRecognizerResultCallbackRecoveryContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
