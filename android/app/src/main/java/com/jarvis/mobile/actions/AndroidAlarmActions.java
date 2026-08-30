package com.jarvis.mobile.actions;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

/** Typed Android alarm capability using local 24-hour clock values. */
public final class AndroidAlarmActions {
    private final Context context;

    public AndroidAlarmActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String setAlarm(String hourText, String minuteText) {
        final int hour;
        final int minute;
        try {
            hour = Integer.parseInt(hourText == null ? "" : hourText.trim());
            minute = Integer.parseInt(minuteText == null ? "" : minuteText.trim());
        } catch (NumberFormatException invalid) {
            return "Tell me a valid alarm time using hour and minute.";
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "Alarm time must use a 24-hour clock with minutes from 0 to 59.";
        }

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS alarm")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible alarm app is available.";
            }
            context.startActivity(intent);
            return String.format(java.util.Locale.ROOT, "Alarm set for %02d:%02d.", hour, minute);
        } catch (SecurityException denied) {
            return "Android blocked that alarm action.";
        } catch (Exception unavailable) {
            return "No compatible alarm app is available.";
        }
    }
}
