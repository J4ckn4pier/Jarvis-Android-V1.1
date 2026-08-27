package com.jarvis.mobile.voice;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;

/** Plays the donor's original response bank by stable resource name. */
public final class LegacyResponsePlayer {
    private final Context context;
    private final SharedPreferences preferences;
    private MediaPlayer current;

    public LegacyResponsePlayer(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences("jarvis_shell", Context.MODE_PRIVATE);
    }

    public synchronized boolean play(String cue) {
        if (!preferences.getBoolean("legacy_cues", true) || cue == null || cue.trim().isEmpty()) {
            return false;
        }
        String safe = cue.toLowerCase().trim();
        if (!safe.matches("[a-z0-9_]{1,64}")) return false;
        int id = context.getResources().getIdentifier(safe, "raw", context.getPackageName());
        if (id == 0) return false;
        release();
        try {
            current = MediaPlayer.create(context, id);
            if (current == null) return false;
            current.setOnCompletionListener(player -> release());
            current.setOnErrorListener((player, what, extra) -> {
                release();
                return true;
            });
            current.start();
            return true;
        } catch (Exception error) {
            release();
            return false;
        }
    }

    public synchronized void release() {
        if (current == null) return;
        try { current.stop(); } catch (Exception ignored) { }
        try { current.release(); } catch (Exception ignored) { }
        current = null;
    }
}
