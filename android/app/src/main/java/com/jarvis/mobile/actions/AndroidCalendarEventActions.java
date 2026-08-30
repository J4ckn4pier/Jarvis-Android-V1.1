package com.jarvis.mobile.actions;

import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;

import java.util.Arrays;

/** Typed calendar-event composer. Android's event editor owns final save and invitation confirmation. */
public final class AndroidCalendarEventActions {
    private final Context context;

    public AndroidCalendarEventActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String prepare(String title, String startMillisText, String endMillisText,
                          String location, String attendees) {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) return "Tell me the event title.";

        final long start;
        final long end;
        try {
            start = Long.parseLong(startMillisText == null ? "" : startMillisText.trim());
            end = Long.parseLong(endMillisText == null ? "" : endMillisText.trim());
        } catch (NumberFormatException invalid) {
            return "I need a valid start and end time for that event.";
        }
        if (start <= 0L || end <= start) return "The event end time must be after its start time.";

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, cleanTitle)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        String cleanLocation = location == null ? "" : location.trim();
        if (!cleanLocation.isEmpty()) intent.putExtra(CalendarContract.Events.EVENT_LOCATION, cleanLocation);

        String cleanAttendees = attendees == null ? "" : attendees.trim();
        if (!cleanAttendees.isEmpty()) {
            String[] emails = Arrays.stream(cleanAttendees.split("[,;]"))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toArray(String[]::new);
            if (emails.length > 0) intent.putExtra(Intent.EXTRA_EMAIL, emails);
        }

        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return "No compatible calendar app is available.";
            }
            context.startActivity(intent);
            return "Event details are ready for your confirmation.";
        } catch (SecurityException denied) {
            return "Android blocked calendar event setup because a required permission is off.";
        } catch (Exception failure) {
            return "Calendar event setup failed: " +
                    (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }
}
