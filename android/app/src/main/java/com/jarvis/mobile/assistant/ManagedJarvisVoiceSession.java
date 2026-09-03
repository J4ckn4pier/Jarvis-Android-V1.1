package com.jarvis.mobile.assistant;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

/**
 * Small lifecycle wrapper around the production JARVIS voice session.
 *
 * Passive wake owns an on-device SpeechRecognizer while JARVIS is idle. The active assistant
 * session also needs a SpeechRecognizer for conversation, so Android must never be asked to run
 * both at once. This wrapper pauses passive wake whenever the assistant overlay is shown and
 * re-arms it whenever the overlay is hidden. That also makes Jarvis / Hey Jarvis reusable after
 * the first successful wake instead of becoming a one-shot service-lifetime feature.
 */
final class ManagedJarvisVoiceSession extends JarvisVoiceSession {
    private static final String TAG = "JARVIS_VOICE_SESSION";

    ManagedJarvisVoiceSession(Context context) {
        super(context);
    }

    @Override public void onShow(Bundle args, int flags) {
        JarvisVoiceInteractionService.pausePassiveWakeForSession();
        try {
            super.onShow(args, flags);
            if (lockScreenSessionWillBeRejected()) {
                // Samsung/OEM builds are not guaranteed to deliver onHide after an immediate
                // keyguard rejection. Restore passive mic ownership now instead of waiting for it.
                JarvisVoiceInteractionService.rearmPassiveWakeAfterSession();
            }
        } catch (RuntimeException showFailure) {
            // Samsung/OEM session setup can fail after passive wake has already released the mic.
            // Restore idle microphone ownership before preserving the platform lifecycle failure.
            JarvisVoiceInteractionService.rearmPassiveWakeAfterSession();
            throw showFailure;
        }
    }

    private boolean lockScreenSessionWillBeRejected() {
        SharedPreferences preferences = getContext().getSharedPreferences("jarvis_shell", Context.MODE_PRIVATE);
        if (preferences.getBoolean("lock_screen_assistant_enabled", true)) return false;
        try {
            KeyguardManager keyguard = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
            return keyguard != null && keyguard.isDeviceLocked();
        } catch (RuntimeException lockStateFailure) {
            Log.w(TAG, "Lock-screen state probe failed while restoring passive wake", lockStateFailure);
            return true;
        }
    }

    @Override public void onHide() {
        try {
            super.onHide();
        } finally {
            JarvisVoiceInteractionService.rearmPassiveWakeAfterSession();
        }
    }

    @Override public void onDestroy() {
        try {
            super.onDestroy();
        } finally {
            // Some Samsung/OEM lifecycle paths can destroy or replace a VoiceInteractionSession
            // without delivering the ordinary hide callback. Never leave passive mic ownership
            // stranded in the session-active state after terminal session teardown.
            JarvisVoiceInteractionService.rearmPassiveWakeAfterSession();
        }
    }
}
