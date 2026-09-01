package com.jarvis.mobile.assistant;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
    private static final long OFFLINE_SUPPORT_RETRY_MS = 2500L;
    private static final long END_OF_SPEECH_WATCHDOG_MS = 3000L;
    private static final long READY_WATCHDOG_MS = 4000L;
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
    private boolean wakeDispatched;
    private long recognizerGeneration;
    private String status = "stopped";

    AndroidOnDeviceWakeWordDetector(Context context) {
        this.context = context.getApplicationContext();
        requestedLanguageTag = configuredLanguageTag(this.context);
        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, requestedLanguageTag);
    }

    private static String configuredLanguageTag(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("jarvis_shell", Context.MODE_PRIVATE);
        String tag = preferences.getString("language", "system");
        if (tag == null || tag.isBlank() || "system".equalsIgnoreCase(tag)) {
            return Locale.getDefault().toLanguageTag();
        }
        Locale configured = Locale.forLanguageTag(tag);
        return configured.getLanguage().isBlank()
                ? Locale.getDefault().toLanguageTag()
                : configured.toLanguageTag();
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
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "microphone permission missing";
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
        wakeDispatched = false;

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
        main.removeCallbacks(offlineSupportRetry);
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            final long generation = ++recognizerGeneration;
            recognizer.checkRecognitionSupport(intent, context.getMainExecutor(), new RecognitionSupportCallback() {
                @Override public void onSupportResult(RecognitionSupport support) {
                    handleOfflineSupportResultSafely(generation, support);
                }

                @Override public void onError(int error) {
                    if (!running || generation != recognizerGeneration) return;
                    status = "Android could not prove offline speech support (error " + error + ")";
                    failClosedAfterSupportCheck();
                }
            });
            return true;
        } catch (RuntimeException failure) {
            status = "could not verify Android offline recognizer: " + failure.getClass().getSimpleName();
            Log.w(TAG, "JARVIS_WAKE_OFFLINE_SUPPORT_CHECK_FAILED", failure);
            scheduleOfflineSupportRetry();
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private void handleOfflineSupportResultSafely(long generation, RecognitionSupport support) {
        if (!running || generation != recognizerGeneration) return;
        try {
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
            recognizer.setRecognitionListener(listenerFor(generation));
            main.removeCallbacks(offlineSupportRetry);
            status = "listening with verified offline Android system recognition for Jarvis / Hey Jarvis";
            Log.i(TAG, "JARVIS_WAKE_ENGINE system_recognizer_verified_offline language=" + requestedLanguageTag);
            startListening();
        } catch (RuntimeException supportFailure) {
            if (!running || generation != recognizerGeneration) return;
            status = "Android offline support callback failed: " + supportFailure.getClass().getSimpleName();
            Log.w(TAG, "JARVIS_WAKE_OFFLINE_SUPPORT_CALLBACK_FAILED", supportFailure);
            scheduleOfflineSupportRetry();
        }
    }

    private void scheduleOfflineSupportRetry() {
        if (!running || wakeDispatched) return;
        cancelEndOfSpeechWatchdog();
        cancelReadyWatchdog();
        listening = false;
        systemOfflineVerified = false;
        usingDedicatedOnDeviceRecognizer = false;
        recognizerGeneration++;
        main.removeCallbacks(restart);
        main.removeCallbacks(recreateAndRestart);
        main.removeCallbacks(offlineSupportRetry);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
            try { recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        main.postDelayed(offlineSupportRetry, OFFLINE_SUPPORT_RETRY_MS);
    }

    private final Runnable offlineSupportRetry = () -> {
        if (!running || wakeDispatched || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        status = "retrying installed offline speech support verification";
        beginSystemOfflineVerification();
    };

    private void failClosedAfterSupportCheck() {
        cancelEndOfSpeechWatchdog();
        cancelReadyWatchdog();
        listening = false;
        running = false;
        systemOfflineVerified = false;
        recognizerGeneration++;
        main.removeCallbacks(offlineSupportRetry);
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

    private RecognitionListener listenerFor(long generation) {
        return new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                if (generation != recognizerGeneration) return;
                AndroidOnDeviceWakeWordDetector.this.onReadyForSpeech(params);
            }

            @Override public void onBeginningOfSpeech() {
                if (generation != recognizerGeneration) return;
                AndroidOnDeviceWakeWordDetector.this.onBeginningOfSpeech();
            }

            @Override public void onRmsChanged(float rmsdB) {
                if (generation != recognizerGeneration) return;
                AndroidOnDeviceWakeWordDetector.this.onRmsChanged(rmsdB);
            }

            @Override public void onBufferReceived(byte[] buffer) {
                if (generation != recognizerGeneration) return;
                AndroidOnDeviceWakeWordDetector.this.onBufferReceived(buffer);
            }

            @Override public void onEndOfSpeech() {
                if (generation != recognizerGeneration) return;
                AndroidOnDeviceWakeWordDetector.this.onEndOfSpeech();
            }

            @Override public void onError(int error) {
                if (generation != recognizerGeneration) return;
                AndroidOnDeviceWakeWordDetector.this.onError(error);
            }

            @Override public void onResults(Bundle results) {
                if (generation != recognizerGeneration) return;
                AndroidOnDeviceWakeWordDetector.this.onResults(results);
            }

            @Override public void onPartialResults(Bundle partialResults) {
                if (generation != recognizerGeneration) return;
                AndroidOnDeviceWakeWordDetector.this.onPartialResults(partialResults);
            }

            @Override public void onEvent(int eventType, Bundle params) {
                if (generation != recognizerGeneration) return;
                AndroidOnDeviceWakeWordDetector.this.onEvent(eventType, params);
            }
        };
    }

    private boolean recognitionAvailableSafely() {
        try {
            return isAvailable(context);
        } catch (RuntimeException availabilityFailure) {
            status = "Android recognizer availability check failed: " + availabilityFailure.getClass().getSimpleName();
            Log.w(TAG, "JARVIS_WAKE_AVAILABILITY_CHECK_FAILED", availabilityFailure);
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
    private boolean recreateRecognizer() {
        cancelEndOfSpeechWatchdog();
        cancelReadyWatchdog();
        listening = false;
        usingDedicatedOnDeviceRecognizer = false;
        recognizerGeneration++;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
            try { recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        if (!running || !recognitionAvailableSafely()) return false;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "microphone permission missing";
            running = false;
            return false;
        }
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
            final long generation = ++recognizerGeneration;
            recognizer.setRecognitionListener(listenerFor(generation));
            return true;
        } catch (RuntimeException failure) {
            status = "could not create Android recognizer: " + failure.getClass().getSimpleName();
            Log.w(TAG, "JARVIS_WAKE_RECOGNIZER_CREATE_FAILED", failure);
            return false;
        }
    }

    private void startListening() {
        cancelEndOfSpeechWatchdog();
        cancelReadyWatchdog();
        if (!running || wakeDispatched || recognizer == null || listening) return;
        if (!usingDedicatedOnDeviceRecognizer && !systemOfflineVerified) {
            status = "offline recognition is not verified";
            failClosedAfterSupportCheck();
            return;
        }
        try {
            listening = true;
            recognizer.startListening(intent);
            main.postDelayed(readyWatchdog, READY_WATCHDOG_MS);
        } catch (RuntimeException failure) {
            listening = false;
            status = "Android wake listen failed: " + failure.getClass().getSimpleName();
            Log.w(TAG, "JARVIS_WAKE_LISTEN_FAILED", failure);
            scheduleRecreate(RECREATE_DELAY_MS);
        }
    }

    private void scheduleRestart(long delayMs) {
        if (!running || wakeDispatched) return;
        cancelEndOfSpeechWatchdog();
        cancelReadyWatchdog();
        main.removeCallbacks(offlineSupportRetry);
        main.removeCallbacks(restart);
        main.removeCallbacks(recreateAndRestart);
        main.postDelayed(restart, delayMs);
    }

    private void scheduleRecreate(long delayMs) {
        if (!running || wakeDispatched) return;
        cancelEndOfSpeechWatchdog();
        cancelReadyWatchdog();
        main.removeCallbacks(offlineSupportRetry);
        main.removeCallbacks(restart);
        main.removeCallbacks(recreateAndRestart);
        main.postDelayed(recreateAndRestart, delayMs);
    }

    private void cancelEndOfSpeechWatchdog() {
        main.removeCallbacks(endOfSpeechWatchdog);
    }

    private void cancelReadyWatchdog() {
        main.removeCallbacks(readyWatchdog);
    }

    private final Runnable endOfSpeechWatchdog = () -> {
        if (!running || wakeDispatched || listening) return;
        status = "wake recognizer stalled after end of speech; recovering";
        Log.w(TAG, "JARVIS_WAKE_END_OF_SPEECH_STALL");
        scheduleRecreate(RECREATE_DELAY_MS);
    };

    private final Runnable readyWatchdog = () -> {
        if (!running || wakeDispatched || !listening) return;
        listening = false;
        status = "wake recognizer stalled before ready; recovering";
        Log.w(TAG, "JARVIS_WAKE_READY_STALL");
        scheduleRecreate(RECREATE_DELAY_MS);
    };

    private final Runnable restart = this::startListening;

    private final Runnable recreateAndRestart = new Runnable() {
        @Override public void run() {
            if (!running || wakeDispatched) return;
            if (!recreateRecognizer()) {
                if (!running) return;
                status = "Android recognizer unavailable during recovery";
                scheduleRecreate(2500L);
                return;
            }
            status = usingDedicatedOnDeviceRecognizer
                    ? "on-device recognizer recovered; listening for Jarvis / Hey Jarvis"
                    : "verified offline system recognizer recovered; listening for Jarvis / Hey Jarvis";
            startListening();
        }
    };

    private void stopListeningForWakeHandoff() {
        wakeDispatched = true;
        listening = false;
        running = false;
        recognizerGeneration++;
        cancelEndOfSpeechWatchdog();
        cancelReadyWatchdog();
        main.removeCallbacks(offlineSupportRetry);
        main.removeCallbacks(restart);
        main.removeCallbacks(recreateAndRestart);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
        }
        status = "wake detected; microphone handed to assistant session";
    }

    private void stopInternal() {
        running = false;
        listening = false;
        wakeDispatched = false;
        systemOfflineVerified = false;
        recognizerGeneration++;
        cancelEndOfSpeechWatchdog();
        cancelReadyWatchdog();
        main.removeCallbacks(offlineSupportRetry);
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
        cancelReadyWatchdog();
        cancelEndOfSpeechWatchdog();
        listening = true;
        status = usingDedicatedOnDeviceRecognizer
                ? "listening with Android on-device recognition for Jarvis / Hey Jarvis"
                : "listening with verified offline Android system recognition for Jarvis / Hey Jarvis";
    }

    @Override public void onBeginningOfSpeech() { cancelReadyWatchdog(); }
    @Override public void onRmsChanged(float rmsdB) { cancelReadyWatchdog(); }
    @Override public void onBufferReceived(byte[] buffer) { cancelReadyWatchdog(); }
    @Override public void onEndOfSpeech() {
        if (!running || wakeDispatched) return;
        cancelReadyWatchdog();
        listening = false;
        cancelEndOfSpeechWatchdog();
        main.postDelayed(endOfSpeechWatchdog, END_OF_SPEECH_WATCHDOG_MS);
    }

    @Override public void onError(int error) {
        cancelReadyWatchdog();
        cancelEndOfSpeechWatchdog();
        listening = false;
        if (!running || wakeDispatched) return;
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            status = "microphone permission missing";
            failClosedAfterSupportCheck();
            return;
        }
        switch (error) {
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT,
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
        cancelReadyWatchdog();
        cancelEndOfSpeechWatchdog();
        listening = false;
        inspect(results);
        scheduleRestart(RESTART_DELAY_MS);
    }

    @Override public void onPartialResults(Bundle partialResults) {
        cancelReadyWatchdog();
        inspect(partialResults);
    }
    @Override public void onEvent(int eventType, Bundle params) { cancelReadyWatchdog(); }

    private void inspect(Bundle bundle) {
        if (!running || wakeDispatched || bundle == null) return;
        ArrayList<String> candidates = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (candidates == null) return;
        for (String candidate : candidates) {
            if (candidate != null && WAKE.matcher(candidate).find()) {
                Runnable callback = onWake;
                if (callback != null) {
                    Log.i(TAG, "JARVIS_WAKE_MATCH phrase=" + candidate);
                    stopListeningForWakeHandoff();
                    callback.run();
                }
                return;
            }
        }
    }
}
