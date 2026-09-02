package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Samsung/OEM result/callback and recognition-service disconnect failures must use bounded service recovery. */
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

        int errorStart = session.indexOf("@Override public void onError(int error)");
        int errorEnd = session.indexOf("@Override public void onResults", errorStart + 1);
        String errorBody = errorStart >= 0 ? session.substring(errorStart, errorEnd > errorStart ? errorEnd : session.length()) : "";
        check(errorBody.contains("SpeechRecognizer.ERROR_SERVER_DISCONNECTED"),
                "Android 12+ speech-service disconnect must be treated as a Samsung/OEM service failure");
        check(errorBody.contains("SpeechRecognizer.ERROR_TOO_MANY_REQUESTS"),
                "recognizer rate limiting must enter bounded recovery instead of the 180ms retry loop");
        check(errorBody.contains("scheduleRecognitionServiceRecovery();"),
                "service disconnect/rate-limit handling must use bounded speech-service recovery");

        System.out.println("AndroidVoiceRecognizerResultCallbackRecoveryContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
