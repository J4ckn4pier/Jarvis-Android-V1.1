package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Passive wake lifecycle work must stay on Android's main thread and yield the microphone to every active JARVIS conversation surface. */
public final class AndroidPassiveWakeMainThreadContractTest {
    public static void main(String[] args) throws Exception {
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));
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
        System.out.println("AndroidPassiveWakeMainThreadContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
