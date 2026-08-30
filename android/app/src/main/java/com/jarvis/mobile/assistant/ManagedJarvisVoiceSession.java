package com.jarvis.mobile.assistant;

import android.content.Context;
import android.os.Bundle;

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
    ManagedJarvisVoiceSession(Context context) {
        super(context);
    }

    @Override public void onShow(Bundle args, int flags) {
        JarvisVoiceInteractionService.pausePassiveWakeForSession();
        super.onShow(args, flags);
    }

    @Override public void onHide() {
        try {
            super.onHide();
        } finally {
            JarvisVoiceInteractionService.rearmPassiveWakeAfterSession();
        }
    }
}
