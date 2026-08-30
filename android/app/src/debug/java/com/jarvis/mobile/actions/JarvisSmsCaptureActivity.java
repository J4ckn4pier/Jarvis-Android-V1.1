package com.jarvis.mobile.actions;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/** Debug-only smsto target used by CI to inspect the real approved SMS compose handoff. */
public final class JarvisSmsCaptureActivity extends Activity {
    private static final String TAG = "JARVIS_SMS_CAPTURE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Uri data = intent == null ? null : intent.getData();
        String number = data == null ? "" : data.getSchemeSpecificPart();
        String body = intent == null ? "" : intent.getStringExtra("sms_body");
        Log.i(TAG, "JARVIS_SMS_CAPTURE number=" + safe(number) + " body=" + safe(body));
        finish();
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
