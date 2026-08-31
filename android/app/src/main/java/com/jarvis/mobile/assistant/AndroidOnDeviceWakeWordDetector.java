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
 * Zero-recurring-cost passive wake detector backed only by Android's explicitly on-device
 * recognizer. It deliberately has no generic SpeechRecognizer fallback because that could route
 * idle microphone audio to a network recognition service.
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
        return context != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
    }

    @Override public WakeWordModelDescriptor modelDescriptor() {
        return new WakeWordModelDescriptor(
                "android-on-device-speech-platform",
                1L,
                "platform-managed-not-redistributed",
                "Android system component; model not redistributed by JARVIS",
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
            status = "Android on-device recognition unavailable";
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
        status = "listening locally for Jarvis / Hey Jarvis";
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
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
            try { recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        if (!running || !isAvailable(context)) return false;
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            recognizer.setRecognitionListener(this);
            return true;
        } catch (RuntimeException failure) {
            status = "could not create on-device recognizer: " + failure.getClass().getSimpleName();
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
            status = "on-device listen failed: " + failure.getClass().getSimpleName();
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !recreateRecognizer()) {
                status = "local recognizer unavailable during recovery";
                scheduleRecreate(2500L);
                return;
            }
            status = "local recognizer recovered; listening for Jarvis / Hey Jarvis";
            Log.i(TAG, "JARVIS_WAKE_RECOGNIZER_RECOVERED");
            startListening();
        }
    };

    private void inspect(Bundle results) {
        if (!running || results == null) return;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null) return;
        for (String phrase : matches) {
            if (phrase != null && (phrase.toLowerCase(Locale.ROOT).contains("hey jarvis")
                    || WAKE.matcher(phrase).find())) {
                Runnable callback = onWake;
                status = "wake detected";
                stopInternal();
                Log.i(TAG, "JARVIS_ON_DEVICE_WAKE_MATCH");
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
        status = "local recognizer recovery after error " + error;
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
