package com.jarvis.mobile.actions;

import android.content.Context;
import android.media.AudioManager;

import java.util.Locale;

/** Typed Android volume controls that reject unknown actions instead of guessing. */
public final class AndroidVolumeActions {
    private final Context context;

    public AndroidVolumeActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String control(String action) {
        String clean = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        final int direction;
        final String success;
        switch (clean) {
            case "up", "raise", "louder" -> {
                direction = AudioManager.ADJUST_RAISE;
                success = "Volume raised.";
            }
            case "down", "lower", "quieter" -> {
                direction = AudioManager.ADJUST_LOWER;
                success = "Volume lowered.";
            }
            case "mute" -> {
                direction = AudioManager.ADJUST_MUTE;
                success = "Muted.";
            }
            case "unmute" -> {
                direction = AudioManager.ADJUST_UNMUTE;
                success = "Unmuted.";
            }
            default -> {
                return "Unsupported volume action. Use up, down, louder, quieter, mute, or unmute.";
            }
        }
        try {
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager == null) return "Volume controls are unavailable on this device.";
            manager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
            return success;
        } catch (SecurityException denied) {
            return "Android blocked volume control because a required permission is off.";
        } catch (Exception failure) {
            return "Volume control failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }
}
