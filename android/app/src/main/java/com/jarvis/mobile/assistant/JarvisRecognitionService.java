package com.jarvis.mobile.assistant;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * RecognitionService required by Android's VoiceInteractionService metadata.
 *
 * Android 11 and earlier may implicitly select this component as the device recognizer when
 * JARVIS becomes the active voice-interaction service. JARVIS does not ship a full acoustic
 * recognizer, so this service delegates those legacy requests to an installed external Android
 * RecognitionService while explicitly excluding itself to prevent recursion. Android 12+
 * ignores the voice-interaction recognitionService selector, while interactive JARVIS speech
 * continues to use SpeechRecognizer directly from JarvisVoiceSession.
 */
public final class JarvisRecognitionService extends RecognitionService {
    private static final String TAG = "JARVIS_RECOGNITION";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Callback, SpeechRecognizer> active = new IdentityHashMap<>();

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        mainHandler.post(() -> startDelegatedRecognition(recognizerIntent, listener));
    }

    private void startDelegatedRecognition(Intent recognizerIntent, Callback listener) {
        release(listener, true);
        ComponentName component = findExternalRecognitionService();
        if (component == null) {
            safeError(listener, SpeechRecognizer.ERROR_CLIENT);
            Log.w(TAG, "No external RecognitionService is available for legacy delegation");
            return;
        }

        SpeechRecognizer recognizer;
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this, component);
        } catch (RuntimeException failure) {
            safeError(listener, SpeechRecognizer.ERROR_CLIENT);
            Log.w(TAG, "Unable to create delegated recognizer " + component.flattenToShortString(), failure);
            return;
        }

        active.put(listener, recognizer);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { safe(() -> listener.readyForSpeech(params)); }
            @Override public void onBeginningOfSpeech() { safe(listener::beginningOfSpeech); }
            @Override public void onRmsChanged(float rmsdB) { safe(() -> listener.rmsChanged(rmsdB)); }
            @Override public void onBufferReceived(byte[] buffer) { safe(() -> listener.bufferReceived(buffer)); }
            @Override public void onEndOfSpeech() { safe(listener::endOfSpeech); }
            @Override public void onError(int error) {
                safeError(listener, error);
                releaseIfCurrent(listener, recognizer);
            }
            @Override public void onResults(Bundle results) {
                safe(() -> listener.results(results));
                releaseIfCurrent(listener, recognizer);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                safe(() -> listener.partialResults(partialResults));
            }
            @Override public void onEvent(int eventType, Bundle params) {
                // RecognitionService.Callback has no generic event forwarding API. Ignoring this
                // optional provider-specific callback is safer than inventing a false mapping.
            }
        });
        try {
            recognizer.startListening(recognizerIntent == null ? new Intent() : recognizerIntent);
        } catch (RuntimeException failure) {
            safeError(listener, SpeechRecognizer.ERROR_CLIENT);
            releaseIfCurrent(listener, recognizer);
            Log.w(TAG, "Delegated recognizer failed to start", failure);
        }
    }

    @Override
    protected void onStopListening(Callback listener) {
        mainHandler.post(() -> {
            SpeechRecognizer recognizer = active.get(listener);
            if (recognizer == null) {
                safeError(listener, SpeechRecognizer.ERROR_CLIENT);
                return;
            }
            try {
                recognizer.stopListening();
            } catch (RuntimeException failure) {
                safeError(listener, SpeechRecognizer.ERROR_CLIENT);
                release(listener, true);
            }
        });
    }

    @Override
    protected void onCancel(Callback listener) {
        mainHandler.post(() -> release(listener, true));
    }

    private ComponentName findExternalRecognitionService() {
        Intent query = new Intent(RecognitionService.SERVICE_INTERFACE);
        List<ResolveInfo> matches = getPackageManager().queryIntentServices(query, 0);
        if (matches == null || matches.isEmpty()) return null;

        ArrayList<ServiceInfo> candidates = new ArrayList<>();
        for (ResolveInfo match : matches) {
            ServiceInfo serviceInfo = match == null ? null : match.serviceInfo;
            if (serviceInfo == null || !serviceInfo.enabled || !serviceInfo.exported) continue;
            if (!serviceInfo.packageName.equals(getPackageName())) candidates.add(serviceInfo);
        }
        candidates.sort(Comparator.comparingInt(JarvisRecognitionService::providerPriority));
        if (candidates.isEmpty()) return null;
        ServiceInfo serviceInfo = candidates.get(0);
        return new ComponentName(serviceInfo.packageName, serviceInfo.name);
    }

    private static int providerPriority(ServiceInfo serviceInfo) {
        ApplicationInfo app = serviceInfo.applicationInfo;
        if (app != null && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return 0;
        return 1;
    }

    private void releaseIfCurrent(Callback listener, SpeechRecognizer recognizer) {
        if (active.get(listener) != recognizer) return;
        active.remove(listener);
        try { recognizer.destroy(); } catch (RuntimeException ignored) { }
    }

    private void release(Callback listener, boolean cancel) {
        SpeechRecognizer recognizer = active.remove(listener);
        if (recognizer == null) return;
        if (cancel) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
        }
        try { recognizer.destroy(); } catch (RuntimeException ignored) { }
    }

    private static void safeError(Callback listener, int error) {
        safe(() -> listener.error(error));
    }

    private interface CallbackAction { void run() throws Exception; }

    private static void safe(CallbackAction action) {
        try { action.run(); } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        mainHandler.post(() -> {
            ArrayList<SpeechRecognizer> recognizers = new ArrayList<>(active.values());
            active.clear();
            for (SpeechRecognizer recognizer : recognizers) {
                try { recognizer.cancel(); } catch (RuntimeException ignored) { }
                try { recognizer.destroy(); } catch (RuntimeException ignored) { }
            }
        });
        super.onDestroy();
    }
}
