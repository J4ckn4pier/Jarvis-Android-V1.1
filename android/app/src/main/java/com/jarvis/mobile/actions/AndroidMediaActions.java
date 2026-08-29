package com.jarvis.mobile.actions;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;

/** Typed Android media actions that preserve structured tool arguments. */
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
            return "Playing " + clean + ".";
        } catch (SecurityException denied) {
            return "Android blocked that action because its permission is off.";
        } catch (Exception unavailable) {
            return "No compatible media app is available for that request.";
        }
    }
}
