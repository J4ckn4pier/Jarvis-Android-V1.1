package com.jarvis.mobile.assistant;

import android.content.Context;
import android.util.Log;

import com.jarvis.brain.CommercialWakeWordPolicy;
import com.jarvis.brain.WakeWordReleaseTrustRegistry;

import java.util.Optional;

/** Creates only local passive-wake detectors; never streams idle microphone audio to cloud services. */
final class AndroidWakeWordDetectorFactory {
    private static final String TAG = "JARVIS_PASSIVE_WAKE";

    private AndroidWakeWordDetectorFactory() { }

    static WakeWordDetectorPort create(Context context) {
        if (context == null) return new DisabledWakeWordDetector("wake detector context missing");
        Context app = context.getApplicationContext();

        // Prefer Android's system-owned, explicitly on-device recognizer. The model is supplied by
        // the OS and is not bundled/redistributed by JARVIS, so no third-party model license is
        // introduced and idle audio is never sent to a network speech service.
        if (AndroidOnDeviceWakeWordDetector.isAvailable(app)) {
            return new AndroidOnDeviceWakeWordDetector(app);
        }

        // Preserve the existing custom-model path for devices without Android on-device speech.
        // A bundled/local engine may use this path only when the exact model artifact is approved
        // by the release trust registry.
        Optional<AndroidWakeWordModelStore.ApprovedArtifact> approved = new AndroidWakeWordModelStore(context).loadApproved();
        if (approved.isEmpty()) {
            return new DisabledWakeWordDetector("Android on-device recognition unavailable and commercial wake model not configured");
        }

        WakeWordDetectorPort candidate = configuredDetector(app, approved.get());
        if (candidate == null) {
            return new DisabledWakeWordDetector("approved wake model present but local detector engine not attached");
        }

        CommercialWakeWordPolicy.Decision decision = WakeWordReleaseTrustRegistry.currentPolicy().approve(candidate.modelDescriptor());
        if (!decision.approved()) {
            candidate.stop();
            Log.w(TAG, "Passive wake disabled: " + decision.reason());
            return new DisabledWakeWordDetector("commercial wake model rejected: " + decision.reason());
        }
        return candidate;
    }

    private static WakeWordDetectorPort configuredDetector(
            Context context,
            AndroidWakeWordModelStore.ApprovedArtifact artifact) {
        // Preserved extension point for a separately reviewed local custom-model engine.
        return null;
    }
}
