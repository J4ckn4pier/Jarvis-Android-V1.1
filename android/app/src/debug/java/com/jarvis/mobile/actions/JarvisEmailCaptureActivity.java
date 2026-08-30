package com.jarvis.mobile.actions;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/** Debug-only mailto target used by CI to inspect the real compose handoff on Android. */
public final class JarvisEmailCaptureActivity extends Activity {
    private static final String TAG = "JARVIS_EMAIL_CAPTURE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Uri data = intent == null ? null : intent.getData();
        String encoded = data == null ? "" : data.getEncodedSchemeSpecificPart();
        String decoded = data == null ? "" : data.getSchemeSpecificPart();
        String subject = intent == null ? "" : intent.getStringExtra(Intent.EXTRA_SUBJECT);
        CharSequence body = intent == null ? "" : intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        Log.i(TAG, "JARVIS_EMAIL_CAPTURE encoded=" + safe(encoded)
                + " decoded=" + safe(decoded)
                + " subject=" + safe(subject)
                + " body=" + safe(body == null ? "" : body.toString()));
        finish();
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
