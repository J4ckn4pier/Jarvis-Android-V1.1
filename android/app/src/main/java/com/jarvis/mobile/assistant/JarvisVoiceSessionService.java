package com.jarvis.mobile.assistant;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
import android.util.Log;

/** Creates the actual JARVIS assistant session after Android routes an assist invocation. */
public class JarvisVoiceSessionService extends VoiceInteractionSessionService {
    private static final String TEST_TAG = "JARVIS_ASSISTANT_TEST";

    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        Log.i(TEST_TAG, "JARVIS_SESSION_SERVICE_NEW_SESSION");
        return new JarvisVoiceSession(this);
    }
}
