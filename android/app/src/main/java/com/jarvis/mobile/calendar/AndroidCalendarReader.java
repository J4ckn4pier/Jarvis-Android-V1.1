package com.jarvis.mobile.calendar;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Read-only, permission-gated device calendar adapter for the shared JARVIS brain. */
public final class AndroidCalendarReader {
    private final Context context;

    public AndroidCalendarReader(Context context) {
        this.context = context.getApplicationContext();
    }

    public String commitments(String when) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "Android blocked calendar access because Calendar permission is off.";
        }
        long[] window = windowFor(when);
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, window[0]);
        ContentUris.appendId(builder, window[1]);
        String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };
        StringBuilder out = new StringBuilder();
        int count = 0;
        try (Cursor cursor = context.getContentResolver().query(
                builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) return "Calendar data is unavailable on this device.";
            int titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE);
            int beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN);
            int allDayIndex = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY);
            SimpleDateFormat format = new SimpleDateFormat("EEE h:mm a", Locale.getDefault());
            while (cursor.moveToNext() && count < 10) {
                String title = titleIndex < 0 ? "Calendar event" : cursor.getString(titleIndex);
                long begin = beginIndex < 0 ? 0L : cursor.getLong(beginIndex);
                boolean allDay = allDayIndex >= 0 && cursor.getInt(allDayIndex) != 0;
                if (title == null || title.isBlank()) title = "Calendar event";
                if (out.length() > 0) out.append('\n');
                out.append("• ").append(title).append(" — ")
                        .append(allDay ? "all day" : format.format(new Date(begin)));
                count++;
            }
        } catch (SecurityException error) {
            return "Android blocked calendar access because Calendar permission is off.";
        } catch (Exception error) {
            return "Calendar read failed: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
        if (count == 0) return "No calendar commitments found for " + normalizedWhen(when) + ".";
        return out.toString();
    }

    private long[] windowFor(String when) {
        String normalized = normalizedWhen(when).toLowerCase(Locale.ROOT);
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        if (normalized.contains("tomorrow")) {
            start.add(Calendar.DAY_OF_YEAR, 1);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            end.setTimeInMillis(start.getTimeInMillis());
            end.add(Calendar.DAY_OF_YEAR, 1);
        } else if (normalized.contains("week")) {
            end.add(Calendar.DAY_OF_YEAR, 7);
        } else if (normalized.contains("today")) {
            end.set(Calendar.HOUR_OF_DAY, 23);
            end.set(Calendar.MINUTE, 59);
            end.set(Calendar.SECOND, 59);
            end.set(Calendar.MILLISECOND, 999);
        } else {
            end.add(Calendar.DAY_OF_YEAR, 1);
        }
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    private String normalizedWhen(String when) {
        return when == null || when.isBlank() ? "the next 24 hours" : when.trim();
    }
}
