package com.jarvis.mobile.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Debug-only CI hook for proving the system-bound VoiceInteractionService -> session path. */
public final class JarvisAssistantTestReceiver extends BroadcastReceiver {
    private static final String TAG = "JARVIS_ASSISTANT_TEST";
    private static final String TEST_COMMAND_EXTRA = "jarvis_test_command";

    @Override
    public void onReceive(Context context, Intent intent) {
        String testCommand = intent == null ? "" : intent.getStringExtra(TEST_COMMAND_EXTRA);
        boolean triggered = JarvisVoiceInteractionService.requestDebugTestSession(context, testCommand);
        Log.i(TAG, triggered ? "JARVIS_DEBUG_SESSION_REQUEST_ACCEPTED" : "JARVIS_DEBUG_SESSION_REQUEST_REJECTED");
    }
}
