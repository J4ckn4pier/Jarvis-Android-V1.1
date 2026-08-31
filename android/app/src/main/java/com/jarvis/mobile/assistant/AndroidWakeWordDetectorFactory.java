package com.jarvis.mobile.assistant;

import android.content.Context;

/** Creates the zero-token passive-wake detector backed by Android's configured speech service. */
final class AndroidWakeWordDetectorFactory {
    private AndroidWakeWordDetectorFactory() { }

    static WakeWordDetectorPort create(Context context) {
        if (context == null) return new DisabledWakeWordDetector("wake detector context missing");
        Context app = context.getApplicationContext();

        // Prefer Android's dedicated on-device recognizer when the phone exposes it. On Samsung
        // devices where that API reports unavailable, AndroidOnDeviceWakeWordDetector falls back
        // to the configured system SpeechRecognizer with EXTRA_PREFER_OFFLINE. This keeps wake
        // usable without a token-billed API while preserving the same system-assistant boundary.
        if (AndroidOnDeviceWakeWordDetector.isAvailable(app)) {
            return new AndroidOnDeviceWakeWordDetector(app);
        }

        return new DisabledWakeWordDetector(
                "Android speech recognition is unavailable on this device");
    }
}
