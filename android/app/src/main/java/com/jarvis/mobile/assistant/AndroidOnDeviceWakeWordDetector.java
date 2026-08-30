package com.jarvis.mobile.assistant;

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
    private static final long RESTART_DELAY_MS = 350L;
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
        // This detector consumes the OS-owned recognizer model; JARVIS does not bundle or
        // redistribute that model. The descriptor is diagnostic provenance, not a model approval.
        return new WakeWordModelDescriptor(
                "android-on-device-speech-platform",
                1L,
                "platform-managed-not-redistributed",
                "Android system component; model not redistributed by JARVIS",
                true,
                true);
    }

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
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            recognizer.setRecognitionListener(this);
            onWake = wakeCallback;
            running = true;
            status = "listening locally for Jarvis / Hey Jarvis";
            startListening();
            return true;
        } catch (RuntimeException failure) {
            status = "could not start on-device recognizer: " + failure.getClass().getSimpleName();
            stopInternal();
            return false;
        }
    }

    @Override public void stop() {
        if (Looper.myLooper() == Looper.getMainLooper()) stopInternal();
        else main.post(this::stopInternal);
    }

    @Override public boolean isRunning() { return running; }
    @Override public String status() { return status; }

    private void startListening() {
        if (!running || recognizer == null || listening) return;
        try {
            listening = true;
            recognizer.startListening(intent);
        } catch (RuntimeException failure) {
            listening = false;
            status = "on-device listen failed: " + failure.getClass().getSimpleName();
            scheduleRestart(1000L);
        }
    }

    private void scheduleRestart(long delayMs) {
        if (!running) return;
        main.removeCallbacks(restart);
        main.postDelayed(restart, delayMs);
    }

    private final Runnable restart = new Runnable() {
        @Override public void run() { startListening(); }
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
                // Release the microphone before the full assistant session begins listening.
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
        status = "local recognizer retry after error " + error;
        scheduleRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1000L : RESTART_DELAY_MS);
    }

    @Override public void onResults(Bundle results) {
        listening = false;
        inspect(results);
        if (running) scheduleRestart(RESTART_DELAY_MS);
    }

    @Override public void onPartialResults(Bundle partialResults) { inspect(partialResults); }
    @Override public void onEvent(int eventType, Bundle params) { }
}
