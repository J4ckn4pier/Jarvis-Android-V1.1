package com.jarvis.mobile.assistant;

import android.service.voice.VoiceInteractionService;
import android.util.Log;

/** Android system entry point for the active JARVIS voice interaction service. */
public class JarvisVoiceInteractionService extends VoiceInteractionService {
    private static final String TEST_TAG = "JARVIS_ASSISTANT_TEST";

    @Override
    public void onReady() {
        super.onReady();
        Log.i(TEST_TAG, "JARVIS_VOICE_SERVICE_READY");
    }

    @Override
    public void onShutdown() {
        Log.i(TEST_TAG, "JARVIS_VOICE_SERVICE_SHUTDOWN");
        super.onShutdown();
    }
}
