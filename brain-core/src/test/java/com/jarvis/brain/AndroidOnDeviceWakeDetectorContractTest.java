package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** APK sprint: passive wake must use locally proven Android recognition, including a Samsung-safe fallback. */
public final class AndroidOnDeviceWakeDetectorContractTest {
    public static void main(String[] args) throws Exception {
        Path assistant = Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant");
        String factory = Files.readString(assistant.resolve("AndroidWakeWordDetectorFactory.java"));
        Path detectorPath = assistant.resolve("AndroidOnDeviceWakeWordDetector.java");
        check(Files.exists(detectorPath), "production must include a real Android wake detector");
        String detector = Files.readString(detectorPath);
        String detectorLower = detector.toLowerCase(Locale.ROOT);
        String service = Files.readString(assistant.resolve("JarvisVoiceInteractionService.java"));
        Path managedSessionPath = assistant.resolve("ManagedJarvisVoiceSession.java");
        check(Files.exists(managedSessionPath), "assistant must include a lifecycle-managed voice session");
        String managedSession = Files.readString(managedSessionPath);
        String sessionService = Files.readString(assistant.resolve("JarvisVoiceSessionService.java"));
        String settings = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));

        check(factory.contains("AndroidOnDeviceWakeWordDetector.isAvailable(app)"), "factory must require an Android speech service before constructing wake");
        check(factory.contains("new AndroidOnDeviceWakeWordDetector(app)"), "factory must construct the real detector instead of returning the disabled stub");
        check(detector.contains("SpeechRecognizer.isOnDeviceRecognitionAvailable(context)"), "wake detector must prefer Android's dedicated on-device recognizer");
        check(detector.contains("SpeechRecognizer.createOnDeviceSpeechRecognizer(context)"), "wake detector must use the dedicated Android on-device recognizer when exposed");

        // Samsung/OEM fallback may use the default recognizer only after API 33+ support metadata proves
        // the requested language is installed on-device. EXTRA_PREFER_OFFLINE by itself is not proof.
        if (detector.contains("SpeechRecognizer.createSpeechRecognizer(context)")) {
            check(detector.contains("checkRecognitionSupport"), "system recognizer fallback must query RecognitionSupport before passive audio starts");
            check(detector.contains("getInstalledOnDeviceLanguages"), "system recognizer fallback must require an installed on-device language");
            check(detector.contains("EXTRA_PREFER_OFFLINE"), "verified system fallback must still explicitly request offline recognition");
            check(detector.contains("systemOfflineVerified"), "system recognizer must be gated by a durable in-session offline-verification flag");
        }

        check(detectorLower.contains("hey\\\\s+") && detectorLower.contains("jarvis"), "wake detector must recognize both requested invocation forms");
        check(detector.contains("startListening") && detector.contains("RecognitionListener"), "wake detector must actually listen instead of reporting a ready stub");
        check(detector.contains("postDelayed") || detector.contains("startListening(intent)"), "wake detector must continue listening after a non-wake utterance");
        check(detector.contains("recreateRecognizer"), "Samsung recovery path must recreate the recognizer after fatal/client/busy errors instead of retrying a poisoned instance forever");
        check(detector.contains("ERROR_CLIENT") && detector.contains("ERROR_RECOGNIZER_BUSY"), "wake recovery must explicitly handle client and busy recognizer failures seen on OEM speech stacks");

        check(service.contains("pausePassiveWakeForSession"), "voice service must expose a session hook that pauses passive wake while the assistant is active");
        check(service.contains("rearmPassiveWakeAfterSession"), "voice service must expose a session hook that re-arms passive wake after the assistant closes");
        check(managedSession.contains("JarvisVoiceInteractionService.pausePassiveWakeForSession()"), "assistant session must pause passive wake when shown to avoid microphone contention");
        check(managedSession.contains("JarvisVoiceInteractionService.rearmPassiveWakeAfterSession()"), "assistant session must re-arm passive wake when hidden so a second wake phrase can work");
        check(sessionService.contains("new ManagedJarvisVoiceSession(this)"), "Android must actually create the lifecycle-managed session in production");
        check(service.contains("wake_enabled") && service.contains("refreshPassiveWakePreference"), "voice service must honor the user Wake Word setting live");
        check(settings.contains("JarvisVoiceInteractionService.refreshPassiveWakePreference()"), "the visible Wake Word switch must immediately refresh the live passive listener");

        System.out.println("AndroidOnDeviceWakeDetectorContractTest passed");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
