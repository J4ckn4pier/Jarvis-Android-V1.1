package com.jarvis.mobile.assistant;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.jarvis.brain.WakeWordModelDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Passive wake detector backed by Android speech recognition.
 *
 * The dedicated on-device recognizer is preferred. Some Samsung/OEM builds report that dedicated
 * API unavailable even when their default recognition service has an offline language installed.
 * On API 33+, that fallback is allowed only after RecognitionSupport explicitly reports the
 * requested language in getInstalledOnDeviceLanguages(). We never rely on EXTRA_PREFER_OFFLINE
 * alone because Android documents the ordinary recognizer as potentially network-backed.
 */
final class AndroidOnDeviceWakeWordDetector implements WakeWordDetectorPort, RecognitionListener {
    private static final String TAG = "JARVIS_PASSIVE_WAKE";
    private static final long RESTART_DELAY_MS = 500L;
    private static final long RECREATE_DELAY_MS = 1200L;
    private static final Pattern WAKE = Pattern.compile("(?i)(?:^|\\b)(?:hey\\s+)?jarvis(?:\\b|$)");

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String requestedLanguageTag;
    private final Intent intent;
    private SpeechRecognizer recognizer;
    private Runnable onWake;
    private boolean running;
    private boolean listening;
    private boolean usingDedicatedOnDeviceRecognizer;
    private boolean systemOfflineVerified;
    private String status = "stopped";

    AndroidOnDeviceWakeWordDetector(Context context) {
        this.context = context.getApplicationContext();
        requestedLanguageTag = Locale.getDefault().toLanguageTag();
        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, requestedLanguageTag);
    }

    static boolean isAvailable(Context context) {
        return context != null && SpeechRecognizer.isRecognitionAvailable(context);
    }

    @Override public WakeWordModelDescriptor modelDescriptor() {
        String identifier = usingDedicatedOnDeviceRecognizer
                ? "android-on-device-speech-platform"
                : "android-system-speech-platform";
        return new WakeWordModelDescriptor(
                identifier,
                1L,
                "",
                "platform-managed-not-redistributed",
                false,
                true);
    }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            if (!recreateRecognizer()) {
                running = false;
                onWake = null;
                return false;
            }
            status = "listening with Android on-device recognition for Jarvis / Hey Jarvis";
            startListening();
            return true;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            running = false;
            onWake = null;
            status = "Android cannot prove offline recognition on this device version";
            return false;
        }

        status = "verifying installed offline speech support";
        return beginSystemOfflineVerification();
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private boolean beginSystemOfflineVerification() {
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.checkRecognitionSupport(intent, context.getMainExecutor(), new RecognitionSupportCallback() {
                @Override public void onSupportResult(RecognitionSupport support) {
                    if (!running) return;
                    if (!languageInstalledOnDevice(support.getInstalledOnDeviceLanguages(), requestedLanguageTag)) {
                        boolean downloadable = languageMatches(
                                support.getSupportedOnDeviceLanguages(), requestedLanguageTag)
                                || languageMatches(support.getPendingOnDeviceLanguages(), requestedLanguageTag);
                        status = downloadable
                                ? "offline speech model is not installed yet for " + requestedLanguageTag
                                : "default Android recognizer has no installed offline support for " + requestedLanguageTag;
                        failClosedAfterSupportCheck();
                        return;
                    }
                    systemOfflineVerified = true;
                    usingDedicatedOnDeviceRecognizer = false;
                    recognizer.setRecognitionListener(AndroidOnDeviceWakeWordDetector.this);
                    status = "listening with verified offline Android system recognition for Jarvis / Hey Jarvis";
                    Log.i(TAG, "JARVIS_WAKE_ENGINE system_recognizer_verified_offline language=" + requestedLanguageTag);
                    startListening();
                }

                @Override public void onError(int error) {
                    if (!running) return;
                    status = "Android could not prove offline speech support (error " + error + ")";
                    failClosedAfterSupportCheck();
                }
            });
            return true;
        } catch (RuntimeException failure) {
            status = "could not verify Android offline recognizer: " + failure.getClass().getSimpleName();
            Log.w(TAG, "JARVIS_WAKE_OFFLINE_SUPPORT_CHECK_FAILED", failure);
            failClosedAfterSupportCheck();
            return false;
        }
    }

    private void failClosedAfterSupportCheck() {
        listening = false;
        running = false;
        systemOfflineVerified = false;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
            try { recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        onWake = null;
    }

    private static boolean languageInstalledOnDevice(List<String> installed, String requested) {
        return languageMatches(installed, requested);
    }

    private static boolean languageMatches(List<String> languages, String requested) {
        if (languages == null || languages.isEmpty()) return false;
        String normalized = requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT);
        String base = normalized.contains("-") ? normalized.substring(0, normalized.indexOf('-')) : normalized;
        for (String language : languages) {
            if (language == null) continue;
            String candidate = language.trim().toLowerCase(Locale.ROOT);
            if (candidate.equals(normalized) || (!base.isBlank() && (candidate.equals(base) || candidate.startsWith(base + "-")))) {
                return true;
            }
        }
        return false;
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
                systemOfflineVerified = false;
                Log.i(TAG, "JARVIS_WAKE_ENGINE dedicated_on_device");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && systemOfflineVerified) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                Log.i(TAG, "JARVIS_WAKE_ENGINE system_recognizer_verified_offline_recovery");
            } else {
                status = "offline recognition is not verified";
                return false;
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
        if (!usingDedicatedOnDeviceRecognizer && !systemOfflineVerified) {
            status = "offline recognition is not verified";
            failClosedAfterSupportCheck();
            return;
        }
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

    private final Runnable restart = this::startListening;

    private final Runnable recreateAndRestart = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!recreateRecognizer()) {
                status = "Android recognizer unavailable during recovery";
                if (systemOfflineVerified) scheduleRecreate(2500L);
                return;
            }
            status = usingDedicatedOnDeviceRecognizer
                    ? "on-device recognizer recovered; listening for Jarvis / Hey Jarvis"
                    : "verified offline system recognizer recovered; listening for Jarvis / Hey Jarvis";
            startListening();
        }
    };

    private void stopInternal() {
        running = false;
        listening = false;
        systemOfflineVerified = false;
        main.removeCallbacks(restart);
        main.removeCallbacks(recreateAndRestart);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
            try { recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        onWake = null;
        status = "stopped";
    }

    @Override public void onReadyForSpeech(Bundle params) {
        listening = true;
        status = usingDedicatedOnDeviceRecognizer
                ? "listening with Android on-device recognition for Jarvis / Hey Jarvis"
                : "listening with verified offline Android system recognition for Jarvis / Hey Jarvis";
    }

    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { listening = false; }

    @Override public void onError(int error) {
        listening = false;
        if (!running) return;
        switch (error) {
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                status = "wake recognizer needs recovery (error " + error + ")";
                scheduleRecreate(RECREATE_DELAY_MS);
            }
            default -> {
                status = "wake recognizer retrying (error " + error + ")";
                scheduleRestart(RESTART_DELAY_MS);
            }
        }
    }

    @Override public void onResults(Bundle results) {
        listening = false;
        inspect(results);
        scheduleRestart(RESTART_DELAY_MS);
    }

    @Override public void onPartialResults(Bundle partialResults) { inspect(partialResults); }
    @Override public void onEvent(int eventType, Bundle params) { }

    private void inspect(Bundle bundle) {
        if (!running || bundle == null) return;
        ArrayList<String> candidates = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (candidates == null) return;
        for (String candidate : candidates) {
            if (candidate != null && WAKE.matcher(candidate).find()) {
                Runnable callback = onWake;
                if (callback != null) {
                    Log.i(TAG, "JARVIS_WAKE_MATCH phrase=" + candidate);
                    callback.run();
                }
                return;
            }
        }
    }
}
