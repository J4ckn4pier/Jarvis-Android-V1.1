package com.jarvis.mobile.actions;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.provider.MediaStore;
import android.view.KeyEvent;

import java.util.Locale;

/** Typed Android media actions that preserve structured tool arguments and truthful dispatch acknowledgements. */
public final class AndroidMediaActions {
    private final Context context;

    public AndroidMediaActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String playMediaQuery(String query) {
        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty()) return "Tell me what you want me to play.";

        Intent intent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(SearchManager.QUERY, clean)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible media app is available for that request.";
            }
            context.startActivity(intent);
            return "Asked a media app to play " + clean + ".";
        } catch (SecurityException denied) {
            return "Android blocked that action because its permission is off.";
        } catch (Exception unavailable) {
            return "No compatible media app is available for that request.";
        }
    }

    public String control(String action) {
        String clean = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        int keyCode;
        String success;
        switch (clean) {
            case "pause" -> {
                keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE;
                success = "Sent pause command.";
            }
            case "play", "resume" -> {
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY;
                success = "Sent play command.";
            }
            case "next", "skip" -> {
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
                success = "Sent next-track command.";
            }
            case "previous", "back" -> {
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                success = "Sent previous-track command.";
            }
            default -> {
                return "Unsupported media action. Use pause, play, resume, next, skip, previous, or back.";
            }
        }

        try {
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager == null) return "Media controls are unavailable on this device.";
            manager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            manager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            return success;
        } catch (SecurityException denied) {
            return "Android blocked media control because a required permission is off.";
        } catch (Exception failure) {
            return "Media control failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }
}
