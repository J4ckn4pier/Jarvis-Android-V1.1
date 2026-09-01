package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Passive wake lifecycle work must stay on Android's main thread and yield the microphone to every active JARVIS conversation surface. */
public final class AndroidPassiveWakeMainThreadContractTest {
    public static void main(String[] args) throws Exception {
        Path assistant = Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant");
        String service = Files.readString(assistant.resolve("JarvisVoiceInteractionService.java"));
        String detector = Files.readString(assistant.resolve("AndroidOnDeviceWakeWordDetector.java"));
        String activity = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        check(service.contains("Handler(Looper.getMainLooper())"),
                "voice interaction service must own a main-thread dispatcher for passive wake lifecycle work");
        check(service.contains("Looper.myLooper() == Looper.getMainLooper()"),
                "passive wake refresh must detect whether it is already on Android's main thread");
        check(service.contains("main.post"),
                "passive wake refresh must marshal off-main requests onto Android's main thread");
        check(activity.contains("JarvisVoiceInteractionService.pausePassiveWakeForSession()"),
                "full JARVIS microphone conversations must pause passive wake before opening their own recognizer");
        check(activity.contains("JarvisVoiceInteractionService.rearmPassiveWakeAfterSession()"),
                "full JARVIS microphone conversations must re-arm passive wake when the conversation ends");
        check(detector.contains("private void inspectSafely(Bundle bundle, boolean terminal)"),
                "passive wake must isolate malformed Samsung/OEM recognition result Bundles behind a safe callback boundary");
        check(detector.contains("JARVIS_WAKE_RESULTS_CALLBACK_FAILED"),
                "malformed passive recognition results must leave explicit evidence instead of escaping the Android callback");
        check(detector.contains("if (terminal) scheduleRestart(RESTART_DELAY_MS);"),
                "a malformed final recognition Bundle must still re-arm passive wake after the callback failure");
        System.out.println("AndroidPassiveWakeMainThreadContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
