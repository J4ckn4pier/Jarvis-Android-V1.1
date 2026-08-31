package com.jarvis.mobile.assistant;

import android.content.Context;

import com.jarvis.brain.WakeWordReleaseTrustRegistry;

/** Creates the zero-token passive-wake detector backed by Android's configured speech service. */
final class AndroidWakeWordDetectorFactory {
    private AndroidWakeWordDetectorFactory() { }

    static WakeWordDetectorPort create(Context context) {
        if (context == null) return new DisabledWakeWordDetector("wake detector context missing");
        Context app = context.getApplicationContext();

        if (AndroidOnDeviceWakeWordDetector.isAvailable(app)) {
            AndroidOnDeviceWakeWordDetector detector = new AndroidOnDeviceWakeWordDetector(app);
            if (WakeWordReleaseTrustRegistry.isPlatformManagedServiceApproved(detector.modelDescriptor())) {
                return detector;
            }
            return new DisabledWakeWordDetector("Android platform wake service is not approved by this JARVIS release");
        }

        // Any future APK-shipped custom wake model remains subject to the fingerprint policy below.
        // Beta intentionally has no such artifact approved/configured.
        WakeWordReleaseTrustRegistry.currentPolicy();
        return new DisabledWakeWordDetector(
                "commercial wake model not configured and Android speech recognition is unavailable on this device");
    }
}
