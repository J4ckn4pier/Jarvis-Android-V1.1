package com.jarvis.mobile.brain.providers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.jarvis.brain.AssistantSurfaceState;
import com.jarvis.brain.RuntimeSurfacePresentation;
import com.jarvis.mobile.brain.AndroidBrainRuntime;
import com.jarvis.mobile.remote.RemoteGoalStateStore;

/** Debug-only end-to-end proof: natural language -> local cortex -> validated plan -> Android tool. */
public final class LocalAssistantIntelligenceTestReceiver extends BroadcastReceiver {
    private static final String TAG = "LOCAL_ASSISTANT_INTELLIGENCE";

    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        String endpoint = intent == null ? "" : clean(intent.getStringExtra("endpoint"));
        String model = intent == null ? "" : clean(intent.getStringExtra("model"));
        String command = intent == null ? "" : clean(intent.getStringExtra("command"));

        new Thread(() -> {
            SharedPreferences cortex = context.getSharedPreferences("jarvis_cortex", Context.MODE_PRIVATE);
            String previousMode = cortex.getString("mode", CortexProviderFactory.MODE_LOCAL);
            String previousEndpoint = cortex.getString("endpoint", "");
            String previousModel = cortex.getString("model", "");
            try {
                // Keep this test about the local assistant cortex, not remote-goal delegation.
                new RemoteGoalStateStore(context).clearConnection();
                cortex.edit()
                        .putString("mode", CortexProviderFactory.MODE_OPENAI_COMPATIBLE)
                        .putString("endpoint", endpoint)
                        .putString("model", model)
                        .commit();

                AndroidBrainRuntime brain = new AndroidBrainRuntime(context);
                RuntimeSurfacePresentation presentation = brain.handlePresentation(command, 1.0);
                if (presentation.state() != AssistantSurfaceState.ACTION_DONE) {
                    throw new IllegalStateException("unexpected state=" + presentation.state()
                            + " text=" + presentation.text() + " detail=" + presentation.detail());
                }
                if (!presentation.text().toLowerCase(java.util.Locale.ROOT).contains("settings")) {
                    throw new IllegalStateException("expected real settings action result, got: " + presentation.text());
                }
                Log.i(TAG, "LOCAL_ASSISTANT_INTELLIGENCE_PASS state=" + presentation.state()
                        + " text=" + presentation.text());
            } catch (Exception failure) {
                Log.e(TAG, "LOCAL_ASSISTANT_INTELLIGENCE_FAIL " + failure.getClass().getSimpleName()
                        + ": " + String.valueOf(failure.getMessage()), failure);
            } finally {
                cortex.edit()
                        .putString("mode", previousMode == null ? CortexProviderFactory.MODE_LOCAL : previousMode)
                        .putString("endpoint", previousEndpoint == null ? "" : previousEndpoint)
                        .putString("model", previousModel == null ? "" : previousModel)
                        .commit();
                pending.finish();
            }
        }, "jarvis-local-assistant-intelligence-test").start();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
