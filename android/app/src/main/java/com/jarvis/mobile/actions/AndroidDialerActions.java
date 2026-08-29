package com.jarvis.mobile.actions;

import android.content.Context;
import android.content.Intent;

/** Typed Android dialer capability independent of launcher labels or OEM phone-app names. */
public final class AndroidDialerActions {
    private final Context context;

    public AndroidDialerActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String openDialer() {
        Intent intent = new Intent(Intent.ACTION_DIAL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible dialer app is available.";
            }
            context.startActivity(intent);
            return "Dialer opened.";
        } catch (SecurityException denied) {
            return "Android blocked that action because its permission is off.";
        } catch (Exception unavailable) {
            return "No compatible dialer app is available.";
        }
    }
}
