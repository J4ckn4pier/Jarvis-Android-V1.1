package com.jarvis.mobile.brain.providers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jarvis.brain.ReasoningRequest;
import com.jarvis.brain.ReasoningResult;
import com.jarvis.brain.ToolRegistry;

/** Debug-only CI bridge proving Android can reach and parse a user-owned OpenAI-compatible cortex. */
public final class OpenAiCompatibleProviderTestReceiver extends BroadcastReceiver {
    private static final String TAG = "JARVIS_LOCAL_CORTEX_TEST";

    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        String endpoint = intent == null ? null : intent.getStringExtra("endpoint");
        String model = intent == null ? null : intent.getStringExtra("model");
        new Thread(() -> {
            try {
                ToolRegistry tools = ToolRegistry.standard();
                OpenAiCompatibleChatProvider provider = new OpenAiCompatibleChatProvider(endpoint, model, "");
                ReasoningRequest request = new ReasoningRequest(
                        "Give me one short transport-test reply.",
                        "CI_CONTEXT_MARKER_314159",
                        tools.specs());
                ReasoningResult result = provider.proposeReasoning(request, tools);
                if (!"Local cortex transport verified.".equals(result.text()) || result.plan() != null) {
                    throw new IllegalStateException("unexpected typed response: " + result);
                }
                Log.i(TAG, "JARVIS_LOCAL_CORTEX_TEST_PASS provider=" + result.providerId()
                        + " text=" + result.text());
            } catch (Exception failure) {
                Log.e(TAG, "JARVIS_LOCAL_CORTEX_TEST_FAIL " + failure.getClass().getSimpleName()
                        + ": " + String.valueOf(failure.getMessage()), failure);
            } finally {
                pending.finish();
            }
        }, "jarvis-local-cortex-test").start();
    }
}
