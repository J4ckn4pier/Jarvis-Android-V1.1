package com.jarvis.mobile.actions;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Typed Android navigation capability that preserves the destination as structured data. */
public final class AndroidNavigationActions {
    private final Context context;

    public AndroidNavigationActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String navigate(String destination) {
        String clean = destination == null ? "" : destination.trim();
        if (clean.isEmpty()) return "Tell me where you want to go.";

        Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(clean));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible navigation app is available.";
            }
            context.startActivity(intent);
            return "Opening navigation to " + clean + ".";
        } catch (SecurityException denied) {
            return "Android blocked that navigation action.";
        } catch (Exception unavailable) {
            return "No compatible navigation app is available.";
        }
    }
}
