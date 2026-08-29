package com.jarvis.mobile.assistant;

import android.content.Context;
import android.util.Log;

import com.jarvis.brain.CommercialWakeWordPolicy;

/** Creates only commercially approved, local passive-wake detectors. */
final class AndroidWakeWordDetectorFactory {
    private static final String TAG = "JARVIS_PASSIVE_WAKE";

    private AndroidWakeWordDetectorFactory() { }

    static WakeWordDetectorPort create(Context context) {
        WakeWordDetectorPort candidate = configuredDetector(context == null ? null : context.getApplicationContext());
        if (candidate == null) {
            return new DisabledWakeWordDetector("commercial wake model not configured");
        }

        CommercialWakeWordPolicy.Decision decision = new CommercialWakeWordPolicy().approve(candidate.modelDescriptor());
        if (!decision.approved()) {
            candidate.stop();
            Log.w(TAG, "Passive wake disabled: " + decision.reason());
            return new DisabledWakeWordDetector("commercial wake model rejected: " + decision.reason());
        }
        return candidate;
    }

    private static WakeWordDetectorPort configuredDetector(Context context) {
        // Shipping beta intentionally has no bundled detector/model yet. A future adapter may be attached here
        // only with an integrity-pinned model whose redistribution and training-data provenance pass the policy.
        return null;
    }
}
