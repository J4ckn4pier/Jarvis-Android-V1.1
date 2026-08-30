package com.jarvis.mobile.actions;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.util.List;

/** Typed app launcher that requires an exact visible app-label match. */
public final class AndroidAppActions {
    private final Context context;

    public AndroidAppActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String open(String app) {
        String target = app == null ? "" : app.trim();
        if (target.isEmpty()) return "Tell me which app to open.";
        try {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> launchers = context.getPackageManager().queryIntentActivities(query, 0);
            for (ResolveInfo info : launchers) {
                CharSequence labelValue = info.loadLabel(context.getPackageManager());
                String label = labelValue == null ? "" : labelValue.toString().trim();
                if (!label.equalsIgnoreCase(target)) continue;
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(info.activityInfo.packageName);
                if (launch == null) return "I couldn’t launch " + target + ".";
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch);
                return "Opened " + label + ".";
            }
            return "I couldn’t find an installed app named " + target + ".";
        } catch (SecurityException denied) {
            return "Android blocked app launching because a required permission is off.";
        } catch (Exception unavailable) {
            return "I couldn’t launch " + target + ".";
        }
    }
}
