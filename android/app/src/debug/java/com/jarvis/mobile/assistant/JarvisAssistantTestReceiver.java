package com.jarvis.mobile.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;

/** Debug-only CI hook for proving the system-bound VoiceInteractionService -> session path. */
public final class JarvisAssistantTestReceiver extends BroadcastReceiver {
    private static final String TAG = "JARVIS_ASSISTANT_TEST";
    private static final String TEST_COMMAND_EXTRA = "jarvis_test_command";
    private static final String TEST_COMMAND_B64_EXTRA = "jarvis_test_command_b64";

    @Override
    public void onReceive(Context context, Intent intent) {
        String testCommand = decodeTestCommand(intent);
        if (testCommand == null) {
            Log.w(TAG, "JARVIS_DEBUG_SESSION_REQUEST_REJECTED invalid encoded command");
            return;
        }
        boolean triggered = testCommand.isBlank()
                ? JarvisVoiceInteractionService.requestDebugTestSession(context)
                : JarvisVoiceInteractionService.requestDebugTestSession(context, testCommand);
        Log.i(TAG, triggered ? "JARVIS_DEBUG_SESSION_REQUEST_ACCEPTED" : "JARVIS_DEBUG_SESSION_REQUEST_REJECTED");
    }

    private String decodeTestCommand(Intent intent) {
        if (intent == null) return "";
        String encoded = intent.getStringExtra(TEST_COMMAND_B64_EXTRA);
        if (encoded != null && !encoded.isBlank()) {
            try {
                return new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException invalidEncoding) {
                return null;
            }
        }
        String plain = intent.getStringExtra(TEST_COMMAND_EXTRA);
        return plain == null ? "" : plain;
    }
}
