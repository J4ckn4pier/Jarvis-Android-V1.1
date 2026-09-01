package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android/OEM wake handoff and startup failures must not leave passive wake permanently dead. */
public final class AndroidPassiveWakeSessionShowRecoveryContractTest {
    public static void main(String[] args) throws Exception {
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));
        check(service.contains("try {\n            showSession(new Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST);"),
                "wake session handoff must guard Android showSession failures");
        check(service.contains("catch (RuntimeException failure)"),
                "wake session handoff must catch OEM/platform runtime failures");
        check(service.contains("armPassiveWake(\"wake session show failed\")"),
                "failed wake session handoff must explicitly re-arm passive wake");

        check(service.contains("WAKE_SESSION_SHOW_TIMEOUT_MS") && service.contains("wakeSessionShowWatchdog"),
                "wake handoff must have a bounded watchdog for OEM/lock-screen paths where showSession returns but no session is actually shown");
        check(service.contains("main.postDelayed(wakeSessionShowWatchdog, WAKE_SESSION_SHOW_TIMEOUT_MS)"),
                "successful showSession dispatch must arm the wake-session handoff watchdog until onShow claims the microphone");
        check(service.contains("armPassiveWake(\"wake session show timeout\")"),
                "a silent wake-session handoff timeout must re-arm passive wake instead of leaving JARVIS permanently deaf");
        check(service.contains("main.removeCallbacks(wakeSessionShowWatchdog)"),
                "session onShow/pause and shutdown paths must cancel the wake-session handoff watchdog");

        check(service.contains("PASSIVE_WAKE_RETRY_DELAY_MS"),
                "passive wake startup must define a bounded retry delay for transient OEM recognizer failures");
        check(service.contains("main.postDelayed(passiveWakeRetry, PASSIVE_WAKE_RETRY_DELAY_MS)"),
                "a failed passive wake start must schedule another start attempt instead of remaining permanently deaf");
        check(service.contains("main.removeCallbacks(passiveWakeRetry)"),
                "passive wake retry must be cancellable during session handoff, disable, and shutdown");
        check(service.contains("private boolean startPassiveWakeSafely()")
                        && service.contains("catch (RuntimeException startFailure)")
                        && service.contains("JARVIS_PASSIVE_WAKE_START_FAILED"),
                "Samsung/OEM exceptions thrown by wake detector startup must be contained by the voice service");
        check(service.contains("boolean started = startPassiveWakeSafely();"),
                "passive wake arming must never call the OEM detector start path without the service recovery boundary");
        System.out.println("AndroidPassiveWakeSessionShowRecoveryContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
