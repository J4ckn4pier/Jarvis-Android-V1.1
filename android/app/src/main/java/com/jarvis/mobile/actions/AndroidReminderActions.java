package com.jarvis.mobile.actions;

import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;

/** Typed Android reminder adapter. The user confirms the final calendar/reminder details in Android's editor. */
public final class AndroidReminderActions {
    private final Context context;

    public AndroidReminderActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String prepareReminder(String request) {
        String normalized = request == null ? "" : request.trim();
        if (normalized.isBlank()) return "Tell me what you want to remember.";

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, normalized)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return "No compatible calendar app is available.";
        }
        try {
            context.startActivity(intent);
            return "Reminder details are ready for your confirmation.";
        } catch (RuntimeException failure) {
            return "Reminder setup failed: " +
                    (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }
}
