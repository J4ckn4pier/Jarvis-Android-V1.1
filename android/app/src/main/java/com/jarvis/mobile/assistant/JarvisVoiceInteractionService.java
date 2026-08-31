package com.jarvis.mobile.assistant;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

/** Android system entry point for the active JARVIS voice interaction service. */
public class JarvisVoiceInteractionService extends VoiceInteractionService {
    private static final String TEST_TAG = "JARVIS_ASSISTANT_TEST";
    private static final String WAKE_TAG = "JARVIS_PASSIVE_WAKE";
    private static final String TEST_COMMAND_EXTRA = "jarvis_test_command";
    private static volatile JarvisVoiceInteractionService activeInstance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WakeWordDetectorPort wakeWordDetector;

    @Override public void onReady() {
        super.onReady();
        activeInstance = this;
        armPassiveWake("service ready");
        Log.i(TEST_TAG, "JARVIS_VOICE_SERVICE_READY");
    }

    private void showWakeSession() {
        showSession(new Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST);
        Log.i(WAKE_TAG, "JARVIS_PASSIVE_WAKE_TRIGGERED");
    }

    static void pausePassiveWakeForSession() {
        JarvisVoiceInteractionService service = activeInstance;
        if (service != null) service.runWakeLifecycleOnMain(service::pausePassiveWake);
    }

    static void rearmPassiveWakeAfterSession() {
        JarvisVoiceInteractionService service = activeInstance;
        if (service != null) service.runWakeLifecycleOnMain(() -> service.armPassiveWake("assistant session hidden"));
    }

    /** Called by visible settings so changed wake/language configuration affects the live listener now. */
    public static void refreshPassiveWakePreference() {
        JarvisVoiceInteractionService service = activeInstance;
        if (service == null) return;
        service.runWakeLifecycleOnMain(() -> {
            service.pausePassiveWake();
            service.wakeWordDetector = null;
            if (service.wakeEnabled()) service.armPassiveWake("user setting enabled");
        });
    }

    private void runWakeLifecycleOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else main.post(action);
    }

    private boolean wakeEnabled() {
        return getSharedPreferences("jarvis_shell", MODE_PRIVATE).getBoolean("wake_enabled", true);
    }

    private void pausePassiveWake() {
        if (wakeWordDetector == null) return;
        wakeWordDetector.stop();
        Log.i(WAKE_TAG, "JARVIS_PASSIVE_WAKE_PAUSED_FOR_SESSION");
    }

    private void armPassiveWake(String reason) {
        if (!wakeEnabled()) {
            pausePassiveWake();
            Log.i(WAKE_TAG, "JARVIS_PASSIVE_WAKE_DISABLED reason=user setting");
            return;
        }
        if (wakeWordDetector == null) wakeWordDetector = AndroidWakeWordDetectorFactory.create(this);
        if (wakeWordDetector.isRunning()) return;
        boolean started = wakeWordDetector.start(this::showWakeSession);
        Log.i(WAKE_TAG, started
                ? "JARVIS_PASSIVE_WAKE_READY model=" + wakeWordDetector.modelDescriptor().identifier() + " reason=" + reason
                : "JARVIS_PASSIVE_WAKE_DISABLED reason=" + wakeWordDetector.status());
    }

    @Override public void onShutdown() {
        if (wakeWordDetector != null) { wakeWordDetector.stop(); wakeWordDetector = null; }
        if (activeInstance == this) activeInstance = null;
        Log.i(TEST_TAG, "JARVIS_VOICE_SERVICE_SHUTDOWN");
        super.onShutdown();
    }

    public static boolean requestDebugTestSession(Context context) { return requestDebugTestSession(context, ""); }

    public static boolean requestDebugTestSession(Context context, String testCommand) {
        if (context == null || (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return false;
        JarvisVoiceInteractionService service = activeInstance;
        if (service == null) { Log.w(TEST_TAG, "JARVIS_DEBUG_SESSION_TRIGGER_NO_SERVICE"); return false; }
        Bundle args = new Bundle();
        if (testCommand != null && !testCommand.isBlank()) args.putString(TEST_COMMAND_EXTRA, testCommand);
        service.showSession(args, VoiceInteractionSession.SHOW_WITH_ASSIST);
        Log.i(TEST_TAG, "JARVIS_DEBUG_SESSION_TRIGGERED");
        return true;
    }
}
