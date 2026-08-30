package com.jarvis.mobile.brain;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jarvis.brain.ExecutionContext;
import com.jarvis.brain.ExternalResearchGateway;
import com.jarvis.brain.SettingsStore;
import com.jarvis.brain.ToolResult;

import java.util.Map;

/** Debug-only Android transport probe for the provider-neutral production research adapter. */
public final class AndroidExternalResearchGatewayTestReceiver extends BroadcastReceiver {
    private static final String TAG = "JARVIS_RESEARCH_TEST";

    @Override
    public void onReceive(Context context, Intent intent) {
        String endpoint = intent == null ? "" : intent.getStringExtra("endpoint");
        if (endpoint == null) endpoint = "";
        SettingsStore settings = new SettingsStore();
        settings.put(SettingsStore.RESEARCH_ENDPOINT, endpoint);
        ExternalResearchGateway gateway = AndroidExternalResearchGateway.create(context, settings);
        ToolResult result = gateway.discoverPlaces(
                Map.of("category", "CI_RESEARCH_MARKER_271828"),
                new ExecutionContext());
        if (result.status() == ToolResult.Status.SUCCESS
                && result.output().contains("CI_RESEARCH_MARKER_271828")
                && result.output().contains("jarvis-ci-research-stub")
                && result.output().contains("2026-08-30T07:00:00Z")) {
            Log.i(TAG, "JARVIS_RESEARCH_TEST_PASS " + result.output());
        } else {
            Log.e(TAG, "JARVIS_RESEARCH_TEST_FAIL status=" + result.status() + " output=" + result.output());
        }
    }
}
