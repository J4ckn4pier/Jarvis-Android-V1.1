package com.jarvis.mobile.assistant;

import android.content.Context;

/** Creates only local passive-wake detectors; never streams idle microphone audio to cloud services. */
final class AndroidWakeWordDetectorFactory {
    private AndroidWakeWordDetectorFactory() { }

    static WakeWordDetectorPort create(Context context) {
        if (context == null) return new DisabledWakeWordDetector("wake detector context missing");
        Context app = context.getApplicationContext();

        // The production passive-wake implementation is Android's explicitly on-device recognizer.
        // It recognizes Jarvis / Hey Jarvis locally and is hosted by the system-bound
        // VoiceInteractionService. Do not silently substitute a network recognizer for idle audio.
        if (AndroidOnDeviceWakeWordDetector.isAvailable(app)) {
            return new AndroidOnDeviceWakeWordDetector(app);
        }

        // A previous extension point accepted an approved model artifact but then returned null
        // because no local inference engine was actually attached. That made configuration look
        // more complete than it was. Fail closed and state the real requirement instead.
        return new DisabledWakeWordDetector(
                "Android on-device recognition unavailable; a reviewed local wake engine is required on this device");
    }
}
