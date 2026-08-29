package com.jarvis.mobile.assistant;

import android.content.Context;
import android.util.Log;

import com.jarvis.brain.CommercialWakeWordPolicy;

import java.util.Optional;

/** Creates only commercially approved, local passive-wake detectors. */
final class AndroidWakeWordDetectorFactory {
    private static final String TAG = "JARVIS_PASSIVE_WAKE";

    private AndroidWakeWordDetectorFactory() { }

    static WakeWordDetectorPort create(Context context) {
        if (context == null) return new DisabledWakeWordDetector("commercial wake model not configured");
        Context app = context.getApplicationContext();
        Optional<AndroidWakeWordModelStore.ApprovedArtifact> approved = new AndroidWakeWordModelStore(context).loadApproved();
        if (approved.isEmpty()) {
            return new DisabledWakeWordDetector("commercial wake model not configured");
        }

        WakeWordDetectorPort candidate = configuredDetector(app, approved.get());
        if (candidate == null) {
            return new DisabledWakeWordDetector("approved wake model present but local detector engine not attached");
        }

        CommercialWakeWordPolicy.Decision decision = new CommercialWakeWordPolicy().approve(candidate.modelDescriptor());
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
        // Deliberately no bundled inference engine/model in this beta. Attach a local engine here only after
        // its runtime license and this exact model artifact's redistribution/training provenance are approved.
        return null;
    }
}
