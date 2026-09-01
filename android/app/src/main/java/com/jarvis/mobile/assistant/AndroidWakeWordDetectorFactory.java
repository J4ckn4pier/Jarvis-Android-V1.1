package com.jarvis.mobile.assistant;

import android.content.Context;

import com.jarvis.brain.WakeWordReleaseTrustRegistry;

/** Creates the zero-token passive-wake detector backed by Android's configured speech service. */
final class AndroidWakeWordDetectorFactory {
    private AndroidWakeWordDetectorFactory() { }

    static WakeWordDetectorPort create(Context context) {
        if (context == null) return new DisabledWakeWordDetector("wake detector context missing");
        Context app = context.getApplicationContext();

        // Keep the real platform detector across transient Samsung/OEM speech-service startup states.
        // AndroidOnDeviceWakeWordDetector.start() owns availability checks and returns the explicit
        // transient status that JarvisVoiceInteractionService uses for its bounded retry loop.
        AndroidOnDeviceWakeWordDetector detector = new AndroidOnDeviceWakeWordDetector(app);
        if (WakeWordReleaseTrustRegistry.isPlatformManagedServiceApproved(detector.modelDescriptor())) {
            return detector;
        }

        // Any future APK-shipped custom wake model remains subject to the fingerprint policy below.
        // Beta intentionally has no such artifact approved/configured.
        WakeWordReleaseTrustRegistry.currentPolicy();
        return new DisabledWakeWordDetector("Android platform wake service is not approved by this JARVIS release");
    }
}
