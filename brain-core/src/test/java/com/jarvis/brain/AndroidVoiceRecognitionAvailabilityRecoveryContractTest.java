package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Samsung/OEM speech-service and passive-wake microphone handoff failures must recover without hot-looping or overlapping recognizers. */
public final class AndroidVoiceRecognitionAvailabilityRecoveryContractTest {
    public static void main(String[] args) throws Exception {
        String session = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));
        String wakeDetector = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidOnDeviceWakeWordDetector.java"));

        String probeFailurePath = between(session, "if (recognitionAvailable == null) {", "if (!recognitionAvailable) {");
        check(probeFailurePath.contains("scheduleRecognitionServiceRecovery();"), "Samsung/OEM recognition availability probe failure must enter bounded speech-service recovery backoff");
        check(!probeFailurePath.contains("scheduleNextListen();"), "recognition availability probe failure must not hammer the speech service through the normal 180ms relisten loop");

        String passiveStartMethod = between(wakeDetector, "@Override public boolean start(Runnable wakeCallback) {", "@TargetApi(Build.VERSION_CODES.TIRAMISU)\n    private boolean beginSystemOfflineVerification() {");
        check(passiveStartMethod.contains("microphonePermissionGrantedSafely()"), "passive Samsung wake startup must use an exception-safe microphone permission probe before recognizer acquisition");
        check(!passiveStartMethod.contains("context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)"), "passive wake startup must not call the OEM permission framework directly without recovery protection");
        check(passiveStartMethod.contains("recognitionAvailableSafely()"), "passive Samsung wake startup must use an exception-safe recognizer availability probe");
        check(!passiveStartMethod.contains("isAvailable(context)"), "passive wake startup must not call SpeechRecognizer availability directly without OEM exception protection");

        String wakeHandoff = between(wakeDetector, "private void stopListeningForWakeHandoff() {", "private void stopInternal() {");
        check(wakeHandoff.contains("SpeechRecognizer handoffRecognizer = recognizer;") && wakeHandoff.contains("recognizer = null;"), "passive wake handoff must detach the retired recognizer before opening the Assistant session");
        check(wakeHandoff.contains("handoffRecognizer.cancel()"), "passive wake handoff must cancel recognition before opening the Assistant session");
        check(wakeHandoff.contains("handoffRecognizer.destroy()"), "Samsung/OEM passive wake handoff must destroy the recognizer so it cannot retain microphone ownership while active listening starts");

        String passiveErrorRecovery = between(wakeDetector, "@Override public void onError(int error) {\n        cancelReadyWatchdog();", "@Override public void onResults(Bundle results) {\n        cancelReadyWatchdog();");
        String recreateCases = between(passiveErrorRecovery, "case SpeechRecognizer.ERROR_RECOGNIZER_BUSY,", "default -> {");
        check(recreateCases.contains("SpeechRecognizer.ERROR_SERVER,"), "passive Samsung wake must rebuild the recognizer after ERROR_SERVER instead of restarting the same potentially wedged service client");
        check(recreateCases.contains("SpeechRecognizer.ERROR_TOO_MANY_REQUESTS"), "passive Samsung wake must rebuild/back off after ERROR_TOO_MANY_REQUESTS instead of retrying the same recognizer every 500ms");
        check(recreateCases.contains("scheduleRecreate(RECREATE_DELAY_MS);"), "passive speech-service failures must use recognizer recreation recovery");

        check(!wakeDetector.contains("@Override public void onEvent(int eventType, Bundle params) { cancelReadyWatchdog(); }"), "generic Samsung/OEM recognizer events must not cancel the passive ready watchdog before microphone readiness is proven");

        int passiveStart = wakeDetector.indexOf("recognizer.startListening(intent);");
        int passiveWatchdogArm = wakeDetector.indexOf("main.postDelayed(readyWatchdog, READY_WATCHDOG_MS);");
        check(passiveStart >= 0 && passiveWatchdogArm >= 0 && passiveWatchdogArm < passiveStart, "passive Samsung wake must arm its ready watchdog before startListening so an immediate OEM callback cannot arrive before the watchdog exists and leave a stale timeout that destroys a healthy wake recognizer");

        int activeStart = session.indexOf("speechRecognizer.startListening(intent);");
        int activeWatchdogArm = session.indexOf("scheduleRecognitionReadyWatchdog(listeningGeneration);");
        check(activeStart >= 0 && activeWatchdogArm >= 0 && activeWatchdogArm < activeStart, "active Assistant must arm its ready watchdog before startListening so an immediate Samsung/OEM callback cannot cancel the watchdog before it exists and leave a false timeout behind");

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
