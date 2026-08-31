package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Samsung/OEM liveness: end-of-speech must not leave passive wake silently idle forever. */
public final class AndroidPassiveWakeEndOfSpeechRecoveryContractTest {
    public static void main(String[] args) throws Exception {
        Path detectorPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidOnDeviceWakeWordDetector.java");
        String detector = Files.readString(detectorPath);

        check(detector.contains("END_OF_SPEECH_RECOVERY_DELAY_MS"),
                "passive wake must define a bounded end-of-speech recovery timeout");
        check(detector.contains("endOfSpeechRecovery"),
                "passive wake must own a watchdog for OEM recognizers that never return results/errors after onEndOfSpeech");
        check(detector.contains("onEndOfSpeech()") && detector.contains("main.postDelayed(endOfSpeechRecovery, END_OF_SPEECH_RECOVERY_DELAY_MS)"),
                "onEndOfSpeech must arm the watchdog instead of only clearing listening state");
        check(detector.contains("main.removeCallbacks(endOfSpeechRecovery)"),
                "normal recognition completion and lifecycle transitions must cancel the end-of-speech watchdog");
        check(detector.contains("end-of-speech timeout") && detector.contains("scheduleRecreate(RECREATE_DELAY_MS)"),
                "watchdog expiry must recreate the recognizer so a stuck Samsung speech session cannot leave wake silently deaf");

        System.out.println("AndroidPassiveWakeEndOfSpeechRecoveryContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
