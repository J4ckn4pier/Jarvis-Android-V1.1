package com.jarvis.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Debug-only bridge that lets emulator CI open the non-exported production diagnostics screen. */
public final class JarvisDiagnosticsTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !"com.jarvis.mobile.DEBUG_SHOW_DIAGNOSTICS".equals(intent.getAction())) return;
        Intent diagnostics = new Intent(context, DiagnosticsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(diagnostics);
        Log.i("JARVIS_DIAGNOSTICS_TEST", "launched production diagnostics screen");
    }
}
