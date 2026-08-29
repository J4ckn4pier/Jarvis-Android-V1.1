package com.jarvis.mobile.assistant;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

/** Android system entry point for the active JARVIS voice interaction service. */
public class JarvisVoiceInteractionService extends VoiceInteractionService {
    private static final String TEST_TAG = "JARVIS_ASSISTANT_TEST";
    private static final String WAKE_TAG = "JARVIS_PASSIVE_WAKE";
    private static volatile JarvisVoiceInteractionService activeInstance;
    private WakeWordDetectorPort wakeWordDetector;

    @Override
    public void onReady() {
        super.onReady();
        activeInstance = this;
        wakeWordDetector = AndroidWakeWordDetectorFactory.create(this);
        boolean started = wakeWordDetector.start(this::showWakeSession);
        Log.i(WAKE_TAG, started
                ? "JARVIS_PASSIVE_WAKE_READY model=" + wakeWordDetector.modelDescriptor().identifier()
                : "JARVIS_PASSIVE_WAKE_DISABLED reason=" + wakeWordDetector.status());
        Log.i(TEST_TAG, "JARVIS_VOICE_SERVICE_READY");
    }

    private void showWakeSession() {
        showSession(new Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST);
        Log.i(WAKE_TAG, "JARVIS_PASSIVE_WAKE_TRIGGERED");
    }

    @Override
    public void onShutdown() {
        if (wakeWordDetector != null) {
            wakeWordDetector.stop();
            wakeWordDetector = null;
        }
        if (activeInstance == this) activeInstance = null;
        Log.i(TEST_TAG, "JARVIS_VOICE_SERVICE_SHUTDOWN");
        super.onShutdown();
    }

    /**
     * Debug-build emulator hook. Android has already selected/bound this VoiceInteractionService;
     * this merely asks that same service to show its associated session so CI can prove the real
     * service -> session path without privileged shell permissions or OEM key-routing behavior.
     */
    public static boolean requestDebugTestSession(Context context) {
        if (context == null || (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return false;
        }
        JarvisVoiceInteractionService service = activeInstance;
        if (service == null) {
            Log.w(TEST_TAG, "JARVIS_DEBUG_SESSION_TRIGGER_NO_SERVICE");
            return false;
        }
        service.showSession(new Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST);
        Log.i(TEST_TAG, "JARVIS_DEBUG_SESSION_TRIGGERED");
        return true;
    }
}
