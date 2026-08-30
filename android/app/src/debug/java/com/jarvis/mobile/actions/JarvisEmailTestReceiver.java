package com.jarvis.mobile.actions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Debug-only CI hook that invokes the real Android email compose adapter. */
public final class JarvisEmailTestReceiver extends BroadcastReceiver {
    private static final String TAG = "JARVIS_EMAIL_TEST";

    @Override
    public void onReceive(Context context, Intent intent) {
        String result = new AndroidEmailActions(context).prepareEmail(
                "person+tag@example.com",
                "Subject & details",
                "Body line one");
        Log.i(TAG, "JARVIS_EMAIL_ACTION_RESULT " + result);
    }
}
