package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Samsung/OEM speech-service and passive-wake microphone handoff failures must recover without hot-looping or overlapping recognizers. */
public final class AndroidVoiceRecognitionAvailabilityRecoveryContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));
        String wakeDetector = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidOnDeviceWakeWordDetector.java"));

        String probeFailurePath = between(
                session,
                "if (recognitionAvailable == null) {",
                "if (!recognitionAvailable) {");

        check(probeFailurePath.contains("scheduleRecognitionServiceRecovery();"),
                "Samsung/OEM recognition availability probe failure must enter bounded speech-service recovery backoff");
        check(!probeFailurePath.contains("scheduleNextListen();"),
                "recognition availability probe failure must not hammer the speech service through the normal 180ms relisten loop");

        String wakeHandoff = between(
                wakeDetector,
                "private void stopListeningForWakeHandoff() {",
                "private void stopInternal() {");
        check(wakeHandoff.contains("recognizer.cancel()"),
                "passive wake handoff must cancel recognition before opening the Assistant session");
        check(wakeHandoff.contains("recognizer.destroy()"),
                "Samsung/OEM passive wake handoff must destroy the recognizer so it cannot retain microphone ownership while active listening starts");
        check(wakeHandoff.contains("recognizer = null;"),
                "passive wake handoff must clear the retired recognizer before invoking the Assistant session callback");

        System.out.println("AndroidVoiceRecognitionAvailabilityRecoveryContractTest: PASS");
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        if (startIndex < 0) throw new AssertionError("missing start marker: " + start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        if (endIndex < 0) throw new AssertionError("missing end marker: " + end);
        return source.substring(startIndex, endIndex);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
