package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Samsung/OEM transient RecognitionSupport callback errors must not permanently disable passive wake. */
public final class AndroidPassiveWakeSupportErrorRetryContractTest {
    public static void main(String[] args) throws Exception {
        Path detectorPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidOnDeviceWakeWordDetector.java");
        String detector = Files.readString(detectorPath);

        String expectedErrorRecovery = "@Override public void onError(int error) {\n"
                + "                    if (!running || generation != recognizerGeneration) return;\n"
                + "                    status = \"Android offline support verification error \" + error + \"; retrying\";\n"
                + "                    Log.w(TAG, \"JARVIS_WAKE_OFFLINE_SUPPORT_ERROR error=\" + error);\n"
                + "                    scheduleOfflineSupportRetry();\n"
                + "                }";

        check(detector.contains(expectedErrorRecovery),
                "RecognitionSupportCallback.onError must enter the bounded offline-support retry path instead of permanently fail-closing passive wake");
        check(detector.contains("OFFLINE_SUPPORT_RETRY_MS = 2500L")
                        && detector.contains("main.postDelayed(offlineSupportRetry, OFFLINE_SUPPORT_RETRY_MS)"),
                "support-error recovery must remain paced rather than spin on a failing Samsung speech service");
        check(detector.contains("recognizerGeneration++")
                        && detector.contains("main.removeCallbacks(offlineSupportRetry)"),
                "support-error recovery must invalidate stale callbacks and remain cancelable during lifecycle handoff");

        System.out.println("AndroidPassiveWakeSupportErrorRetryContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
