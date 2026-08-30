package com.jarvis.mobile.actions;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Typed app launcher that requires one unique exact visible app-label match. */
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
            Set<String> exactPackages = new LinkedHashSet<>();
            String exactLabel = target;
            for (ResolveInfo info : launchers) {
                CharSequence labelValue = info.loadLabel(context.getPackageManager());
                String label = labelValue == null ? "" : labelValue.toString().trim();
                if (!label.equalsIgnoreCase(target) || info.activityInfo == null || info.activityInfo.packageName == null) continue;
                exactPackages.add(info.activityInfo.packageName);
                exactLabel = label;
            }
            if (exactPackages.isEmpty()) {
                return "I couldn’t find an installed app named " + target + ".";
            }
            if (exactPackages.size() != 1) {
                return "I couldn’t uniquely identify installed app named " + target + " because multiple installed apps use that name.";
            }
            String packageName = exactPackages.iterator().next();
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) return "I couldn’t launch " + target + ".";
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            return "Opened " + exactLabel + ".";
        } catch (SecurityException denied) {
            return "Android blocked app launching because a required permission is off.";
        } catch (Exception unavailable) {
            return "I couldn’t launch " + target + ".";
        }
    }
}
