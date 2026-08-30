package com.jarvis.mobile.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Debug-only emulator bridge for seeding the real encrypted remote-goal state store. */
public final class RemoteGoalStateTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !"com.jarvis.mobile.DEBUG_SEED_REMOTE_GOAL".equals(intent.getAction())) {
            setResultCode(0);
            return;
        }
        try {
            String baseUrl = intent.getStringExtra("base_url");
            String token = intent.getStringExtra("token");
            String projectId = intent.getStringExtra("project_id");
            RemoteGoalStateStore state = new RemoteGoalStateStore(context);
            state.clear();
            state.saveConnection(baseUrl, token);
            if (projectId != null && !projectId.isBlank()) state.saveProject(projectId);
            setResultCode(1);
        } catch (RuntimeException failure) {
            setResultCode(0);
        }
    }
}
