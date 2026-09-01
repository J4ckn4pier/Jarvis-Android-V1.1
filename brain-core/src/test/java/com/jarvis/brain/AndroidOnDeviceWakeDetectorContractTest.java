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

        if (detector.contains("SpeechRecognizer.createSpeechRecognizer(context)")) {
            check(detector.contains("checkRecognitionSupport"), "system recognizer fallback must query RecognitionSupport before passive audio starts");
            check(detector.contains("getInstalledOnDeviceLanguages"), "system recognizer fallback must require an installed on-device language");
            check(detector.contains("EXTRA_PREFER_OFFLINE"), "verified system fallback must still explicitly request offline recognition");
            check(detector.contains("systemOfflineVerified"), "system recognizer must be gated by a durable in-session offline-verification flag");
        }

        check(detector.contains("Manifest.permission.RECORD_AUDIO") && detector.contains("PackageManager.PERMISSION_GRANTED"),
                "passive wake must fail closed before creating/retrying recognizers when microphone permission is missing");
        check(detectorLower.contains("hey\\\\s+") && detectorLower.contains("jarvis"), "wake detector must recognize both requested invocation forms");
        check(detector.contains("startListening") && detector.contains("RecognitionListener"), "wake detector must actually listen instead of reporting a ready stub");
        check(detector.contains("postDelayed") || detector.contains("startListening(intent)"), "wake detector must continue listening after a non-wake utterance");
        check(detector.contains("recreateRecognizer"), "Samsung recovery path must recreate the recognizer after fatal/client/busy errors instead of retrying a poisoned instance forever");
        check(detector.contains("ERROR_CLIENT") && detector.contains("ERROR_RECOGNIZER_BUSY"), "wake recovery must explicitly handle client and busy recognizer failures seen on OEM speech stacks");
        check(detector.contains("status = \"Android recognizer unavailable during recovery\";\n                scheduleRecreate(2500L);"),
                "failed recognizer recreation must keep retrying even when Samsung's dedicated on-device recognizer is the selected engine");

        check(detector.contains("recognizerGeneration"),
                "passive wake must generation-tag recognizers so callbacks from a destroyed Samsung/OEM recognizer cannot affect its replacement");
        check(detector.contains("generation != recognizerGeneration"),
                "every passive recognizer listener/support callback must reject stale generations before mutating wake state");
        check(detector.contains("setRecognitionListener(listenerFor(generation))"),
                "each passive recognizer must receive a generation-bound listener instead of sharing the detector object across replacements");

        check(detector.contains("wakeDispatched"), "wake detector must latch the first matching partial/final result so one phrase cannot trigger duplicate assistant sessions");
        check(detector.contains("stopListeningForWakeHandoff"), "wake detector must have an explicit handoff path that releases passive recognition before showing the assistant");
        check(detector.contains("recognizer.cancel()"), "wake handoff must cancel passive recognition immediately to release the microphone for the assistant session");

        check(detector.contains("END_OF_SPEECH_WATCHDOG_MS")
                        && detector.contains("endOfSpeechWatchdog")
                        && detector.contains("main.postDelayed(endOfSpeechWatchdog, END_OF_SPEECH_WATCHDOG_MS)"),
                "Samsung/OEM onEndOfSpeech without a terminal result/error must have a bounded watchdog so passive wake cannot remain silently deaf");
        check(detector.contains("main.removeCallbacks(endOfSpeechWatchdog)"),
                "every terminal, restart, stop, and wake-handoff path must be able to cancel the passive end-of-speech watchdog");
        check(detector.contains("READY_WATCHDOG_MS")
                        && detector.contains("readyWatchdog")
                        && detector.contains("main.postDelayed(readyWatchdog, READY_WATCHDOG_MS)"),
                "passive wake must recover when Samsung/OEM accepts startListening but never reports recognizer readiness or another callback");
        check(detector.contains("main.removeCallbacks(readyWatchdog)")
                        && detector.contains("wake recognizer stalled before ready; recovering"),
                "passive pre-ready recovery must be cancelable by healthy callbacks and rebuild the wedged recognizer instead of leaving wake silently deaf");

        check(detector.contains("getSharedPreferences(\"jarvis_shell\""), "wake detector must read the same persisted JARVIS language setting as active conversation");
        check(detector.contains("getString(\"language\""), "wake detector must resolve its recognizer language from the visible Language setting");
        check(settings.contains("putString(\"language\", tags[index]).apply();\n                    JarvisVoiceInteractionService.refreshPassiveWakePreference();"),
                "saving a new JARVIS language must immediately rebuild/re-arm passive wake so the live recognizer stops using the previous language");
        check(service.contains("service.pausePassiveWake();")
                        && service.contains("service.wakeWordDetector = null;")
                        && service.contains("if (service.wakeEnabled()) service.armPassiveWake(\"user setting enabled\")"),
                "refreshPassiveWakePreference must stop and discard the running listener before re-arming so changed language/configuration is actually re-read");

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
