package com.jarvis.mobile.assistant;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.jarvis.brain.WakeWordModelDescriptor;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Passive wake detector backed by Android's speech-recognition service. It prefers the dedicated
 * on-device recognizer when Android exposes one, but falls back to the phone's configured system
 * recognizer so Samsung devices that do not expose createOnDeviceSpeechRecognizer can still use
 * "Jarvis" / "Hey Jarvis" without an API key or token-billed service.
 */
final class AndroidOnDeviceWakeWordDetector implements WakeWordDetectorPort, RecognitionListener {
    private static final String TAG = "JARVIS_PASSIVE_WAKE";
    private static final long RESTART_DELAY_MS = 500L;
    private static final long RECREATE_DELAY_MS = 1200L;
    private static final Pattern WAKE = Pattern.compile("(?i)(?:^|\\b)(?:hey\\s+)?jarvis(?:\\b|$)");

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Intent intent;
    private SpeechRecognizer recognizer;
    private Runnable onWake;
    private boolean running;
    private boolean listening;
    private boolean usingDedicatedOnDeviceRecognizer;
    private String status = "stopped";

    AndroidOnDeviceWakeWordDetector(Context context) {
        this.context = context.getApplicationContext();
        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
    }

    static boolean isAvailable(Context context) {
        return context != null && SpeechRecognizer.isRecognitionAvailable(context);
    }

    @Override public WakeWordModelDescriptor modelDescriptor() {
        String identifier = usingDedicatedOnDeviceRecognizer
                ? "android-on-device-speech-platform"
                : "android-system-speech-platform";
        String provenance = usingDedicatedOnDeviceRecognizer
                ? "Android dedicated on-device recognition service"
                : "Android configured speech recognition service; offline preference requested";
        return new WakeWordModelDescriptor(
                identifier,
                1L,
                "platform-managed-not-redistributed",
                provenance,
                true,
                true);
    }

    @TargetApi(Build.VERSION_CODES.S)
    @Override public boolean start(Runnable wakeCallback) {
        if (wakeCallback == null) {
            status = "wake callback missing";
            return false;
        }
        if (!isAvailable(context)) {
            status = "Android speech recognition unavailable";
            return false;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            status = "wake detector must start on Android main thread";
            return false;
        }
        stopInternal();
        onWake = wakeCallback;
        running = true;
        if (!recreateRecognizer()) {
            running = false;
            onWake = null;
            return false;
        }
        status = usingDedicatedOnDeviceRecognizer
                ? "listening with Android on-device recognition for Jarvis / Hey Jarvis"
                : "listening with Android system recognition for Jarvis / Hey Jarvis";
        startListening();
        return true;
    }

    @Override public void stop() {
        if (Looper.myLooper() == Looper.getMainLooper()) stopInternal();
        else main.post(this::stopInternal);
    }

    @Override public boolean isRunning() { return running; }
    @Override public String status() { return status; }

    @TargetApi(Build.VERSION_CODES.S)
    private boolean recreateRecognizer() {
        listening = false;
        usingDedicatedOnDeviceRecognizer = false;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
            try { recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        if (!running || !isAvailable(context)) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
                usingDedicatedOnDeviceRecognizer = true;
                Log.i(TAG, "JARVIS_WAKE_ENGINE dedicated_on_device");
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                Log.i(TAG, "JARVIS_WAKE_ENGINE system_recognizer_fallback");
            }
            recognizer.setRecognitionListener(this);
            return true;
        } catch (RuntimeException failure) {
            status = "could not create Android recognizer: " + failure.getClass().getSimpleName();
            Log.w(TAG, "JARVIS_WAKE_RECOGNIZER_CREATE_FAILED", failure);
            return false;
        }
    }

    private void startListening() {
        if (!running || recognizer == null || listening) return;
        try {
            listening = true;
            recognizer.startListening(intent);
        } catch (RuntimeException failure) {
            listening = false;
            status = "Android wake listen failed: " + failure.getClass().getSimpleName();
            Log.w(TAG, "JARVIS_WAKE_LISTEN_FAILED", failure);
            scheduleRecreate(RECREATE_DELAY_MS);
        }
    }

    private void scheduleRestart(long delayMs) {
        if (!running) return;
        main.removeCallbacks(restart);
        main.removeCallbacks(recreateAndRestart);
        main.postDelayed(restart, delayMs);
    }

    private void scheduleRecreate(long delayMs) {
        if (!running) return;
        main.removeCallbacks(restart);
        main.removeCallbacks(recreateAndRestart);
        main.postDelayed(recreateAndRestart, delayMs);
    }

    private final Runnable restart = new Runnable() {
        @Override public void run() { startListening(); }
    };

    private final Runnable recreateAndRestart = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!recreateRecognizer()) {
                status = "Android recognizer unavailable during recovery";
                scheduleRecreate(2500L);
                return;
            }
            status = usingDedicatedOnDeviceRecognizer
                    ? "on-device recognizer recovered; listening for Jarvis / Hey Jarvis"
                    : "system recognizer recovered; listening for Jarvis / Hey Jarvis";
            Log.i(TAG, "JARVIS_WAKE_RECOGNIZER_RECOVERED");
            startListening();
        }
    };

    private void inspect(Bundle results) {
        if (!running || results == null) return;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null) return;
        for (String phrase : matches) {
            if (phrase != null && WAKE.matcher(phrase).find()) {
                Runnable callback = onWake;
                status = "wake detected";
                stopInternal();
                Log.i(TAG, "JARVIS_WAKE_MATCH phrase=" + phrase);
                if (callback != null) callback.run();
                return;
            }
        }
    }

    private void stopInternal() {
        running = false;
        listening = false;
        main.removeCallbacks(restart);
        main.removeCallbacks(recreateAndRestart);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
            try { recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        onWake = null;
        if (!"wake detected".equals(status)) status = "stopped";
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }

    @Override public void onError(int error) {
        listening = false;
        if (!running) return;
        status = "recognizer recovery after error " + error;
        Log.w(TAG, "JARVIS_WAKE_RECOGNIZER_ERROR code=" + error);
        if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            scheduleRecreate(RECREATE_DELAY_MS);
        } else {
            scheduleRestart(RESTART_DELAY_MS);
        }
    }

    @Override public void onResults(Bundle results) {
        listening = false;
        inspect(results);
        if (running) scheduleRestart(RESTART_DELAY_MS);
    }

    @Override public void onPartialResults(Bundle partialResults) { inspect(partialResults); }
    @Override public void onEvent(int eventType, Bundle params) { }
}
