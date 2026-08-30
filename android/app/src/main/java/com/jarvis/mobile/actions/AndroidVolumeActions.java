package com.jarvis.mobile.actions;

import android.content.Context;
import android.media.AudioManager;

import java.util.Locale;

/** Typed Android media-volume controls that reject unknown actions and report no-op limits truthfully. */
public final class AndroidVolumeActions {
    private final Context context;

    public AndroidVolumeActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String control(String action) {
        String clean = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        try {
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager == null) return "Volume controls are unavailable on this device.";

            int stream = AudioManager.STREAM_MUSIC;
            switch (clean) {
                case "up", "raise", "louder" -> {
                    int current = manager.getStreamVolume(stream);
                    int maximum = manager.getStreamMaxVolume(stream);
                    if (current >= maximum) return "Media volume is already at maximum.";
                    manager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    return "Volume raised.";
                }
                case "down", "lower", "quieter" -> {
                    int current = manager.getStreamVolume(stream);
                    int minimum = manager.getStreamMinVolume(stream);
                    if (current <= minimum) return "Media volume is already at minimum.";
                    manager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    return "Volume lowered.";
                }
                case "mute" -> {
                    if (manager.isStreamMute(stream)) return "Media is already muted.";
                    manager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                    return "Muted.";
                }
                case "unmute" -> {
                    if (!manager.isStreamMute(stream)) return "Media is already unmuted.";
                    manager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                    return "Unmuted.";
                }
                default -> {
                    return "Unsupported volume action. Use up, down, louder, quieter, mute, or unmute.";
                }
            }
        } catch (SecurityException denied) {
            return "Android blocked volume control because a required permission is off.";
        } catch (Exception failure) {
            return "Volume control failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }
}
