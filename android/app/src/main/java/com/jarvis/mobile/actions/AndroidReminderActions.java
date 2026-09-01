package com.jarvis.mobile.actions;

import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;

/** Typed Android reminder adapter. The user confirms the final calendar/reminder details in Android's editor. */
public final class AndroidReminderActions {
    private static final long DEFAULT_REMINDER_EVENT_DURATION_MILLIS = 5L * 60L * 1000L;
    private final Context context;

    public AndroidReminderActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String prepareReminder(String title, String startMillisText) {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isBlank()) return "Tell me what you want to remember.";

        final long start;
        try {
            start = Long.parseLong(startMillisText == null ? "" : startMillisText.trim());
        } catch (NumberFormatException invalid) {
            return "I need a valid reminder time.";
        }
        if (start <= 0L || start > Long.MAX_VALUE - DEFAULT_REMINDER_EVENT_DURATION_MILLIS) {
            return "I need a valid reminder time.";
        }
        long end = start + DEFAULT_REMINDER_EVENT_DURATION_MILLIS;

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, cleanTitle)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
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
