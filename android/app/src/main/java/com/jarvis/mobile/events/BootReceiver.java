package com.jarvis.mobile.events;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jarvis.mobile.memory.JarvisDatabase;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            JarvisDatabase.get(context).logEvent(
                    "system", "Android", "Device booted", "JARVIS local memory is available.");
        }
    }
}
