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
    private static final long PASSIVE_WAKE_RETRY_DELAY_MS = 2500L;
    private static volatile JarvisVoiceInteractionService activeInstance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable passiveWakeRetry = () -> armPassiveWake("retry after transient startup failure");
    private WakeWordDetectorPort wakeWordDetector;

    @Override public void onReady() {
        super.onReady();
        activeInstance = this;
        armPassiveWake("service ready");
        Log.i(TEST_TAG, "JARVIS_VOICE_SERVICE_READY");
    }

    private void showWakeSession() {
        try {
            showSession(new Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST);
            Log.i(WAKE_TAG, "JARVIS_PASSIVE_WAKE_TRIGGERED");
        } catch (RuntimeException failure) {
            Log.w(WAKE_TAG, "JARVIS_PASSIVE_WAKE_SESSION_SHOW_FAILED", failure);
            armPassiveWake("wake session show failed");
        }
    }

    public static void pausePassiveWakeForSession() {
        JarvisVoiceInteractionService service = activeInstance;
        if (service != null) service.runWakeLifecycleOnMain(service::pausePassiveWake);
    }

    public static void rearmPassiveWakeAfterSession() {
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
        main.removeCallbacks(passiveWakeRetry);
        if (wakeWordDetector == null) return;
        wakeWordDetector.stop();
        Log.i(WAKE_TAG, "JARVIS_PASSIVE_WAKE_PAUSED_FOR_SESSION");
    }

    private void armPassiveWake(String reason) {
        main.removeCallbacks(passiveWakeRetry);
        if (!wakeEnabled()) {
            pausePassiveWake();
            Log.i(WAKE_TAG, "JARVIS_PASSIVE_WAKE_DISABLED reason=user setting");
            return;
        }
        if (wakeWordDetector == null) wakeWordDetector = AndroidWakeWordDetectorFactory.create(this);
        if (wakeWordDetector.isRunning()) return;
        boolean started;
        String detectorStatus;
        try {
            started = wakeWordDetector.start(this::showWakeSession);
            detectorStatus = wakeWordDetector.status();
        } catch (RuntimeException failure) {
            detectorStatus = "wake detector start failed: " + failure.getClass().getSimpleName();
            Log.w(WAKE_TAG, "JARVIS_PASSIVE_WAKE_START_FAILED", failure);
            wakeWordDetector = null;
            main.postDelayed(passiveWakeRetry, PASSIVE_WAKE_RETRY_DELAY_MS);
            Log.w(WAKE_TAG, "JARVIS_PASSIVE_WAKE_RETRY_SCHEDULED reason=" + detectorStatus);
            return;
        }
        if (!started && transientWakeStartupFailure(detectorStatus)) {
            main.postDelayed(passiveWakeRetry, PASSIVE_WAKE_RETRY_DELAY_MS);
            Log.w(WAKE_TAG, "JARVIS_PASSIVE_WAKE_RETRY_SCHEDULED reason=" + detectorStatus);
        }
        Log.i(WAKE_TAG, started
                ? "JARVIS_PASSIVE_WAKE_READY model=" + wakeWordDetector.modelDescriptor().identifier() + " reason=" + reason
                : "JARVIS_PASSIVE_WAKE_DISABLED reason=" + detectorStatus);
    }

    private static boolean transientWakeStartupFailure(String status) {
        if (status == null) return false;
        return status.startsWith("could not create Android recognizer")
                || status.startsWith("could not verify Android offline recognizer")
                || status.startsWith("Android speech recognition unavailable");
    }

    @Override public void onShutdown() {
        main.removeCallbacks(passiveWakeRetry);
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
