package com.jarvis.mobile.assistant;

import android.content.Intent;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

/**
 * RecognitionService required by Android's VoiceInteractionService contract.
 * Interactive JARVIS speech recognition currently lives in JarvisVoiceSession.
 */
public final class JarvisRecognitionService extends RecognitionService {
    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        try {
            listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);
        } catch (Exception ignored) {
            // Framework callback binder is gone; no local state to recover.
        }
    }

    @Override
    protected void onStopListening(Callback listener) {
        // No direct RecognitionService session is active in this beta.
    }

    @Override
    protected void onCancel(Callback listener) {
        // No direct RecognitionService session is active in this beta.
    }
}
