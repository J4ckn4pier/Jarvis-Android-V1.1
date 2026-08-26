package com.jarvis.mobile.actions;

import android.Manifest;
import android.app.AlarmManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;

import com.jarvis.mobile.hands.JarvisAccessibilityService;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AndroidActionRouter {
    private static final Pattern DURATION = Pattern.compile(
            "(\\d+)\\s*(second|seconds|minute|minutes|hour|hours|day|days)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK_TIME = Pattern.compile(
            "\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b",
            Pattern.CASE_INSENSITIVE);

    private final Context context;

    public AndroidActionRouter(Context context) {
        this.context = context;
    }

    public String execute(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        try {
            if (lower.startsWith("call ")) return call(raw.substring(5).trim());
            if (lower.startsWith("dial ")) return dial(raw.substring(5).trim());

            if (lower.startsWith("send a text to ")) {
                return text(raw.substring("send a text to ".length()).trim());
            }
            if (lower.startsWith("send text to ")) {
                return text(raw.substring("send text to ".length()).trim());
            }
            if (lower.startsWith("text ")) return text(raw.substring(5).trim());
            if (lower.startsWith("sms ")) return text(raw.substring(4).trim());
            if (lower.startsWith("message ")) return text(raw.substring(8).trim());

            if (lower.startsWith("send an email to ")) {
                return email(raw.substring("send an email to ".length()).trim());
            }
            if (lower.startsWith("send email to ")) {
                return email(raw.substring("send email to ".length()).trim());
            }
            if (lower.startsWith("email ")) return email(raw.substring(6).trim());

            if (lower.startsWith("add calendar event ")) {
                return calendarEvent(raw.substring("add calendar event ".length()).trim());
            }
            if (lower.startsWith("create event ")) {
                return calendarEvent(raw.substring("create event ".length()).trim());
            }
            if (lower.startsWith("add event ")) {
                return calendarEvent(raw.substring("add event ".length()).trim());
            }
            if (lower.startsWith("schedule ")) return calendarEvent(raw.substring(9).trim());
            if (lower.startsWith("calendar ")) return calendarEvent(raw.substring(9).trim());

            if (lower.startsWith("navigate to ")) return navigate(raw.substring(12).trim());
            if (lower.startsWith("directions to ")) return navigate(raw.substring(14).trim());
            if (lower.startsWith("take me to ")) return navigate(raw.substring(11).trim());

            if (lower.startsWith("open ")) return openTarget(raw.substring(5).trim());
            if (lower.startsWith("launch ")) return openTarget(raw.substring(7).trim());

            if (lower.startsWith("search for ")) return search(raw.substring(11).trim());
            if (lower.startsWith("search ")) return search(raw.substring(7).trim());
            if (lower.startsWith("look up ")) return search(raw.substring(8).trim());

            if (lower.startsWith("set a timer for ")) return timer(lower.substring(16));
            if (lower.startsWith("set timer for ")) return timer(lower.substring(14));
            if (lower.startsWith("start a timer for ")) return timer(lower.substring(18));
            if (lower.startsWith("timer ")) return timer(lower.substring(6));

            if (lower.startsWith("set an alarm for ")) return alarm(lower.substring(17));
            if (lower.startsWith("set alarm for ")) return alarm(lower.substring(14));
            if (lower.startsWith("alarm ")) return alarm(lower.substring(6));

            if (lower.contains("flashlight on") || lower.contains("turn on the flashlight") ||
                    lower.contains("turn the flashlight on") || lower.contains("torch on")) {
                return torch(true);
            }
            if (lower.contains("flashlight off") || lower.contains("turn off the flashlight") ||
                    lower.contains("turn the flashlight off") || lower.contains("torch off")) {
                return torch(false);
            }

            if (lower.contains("volume up") || lower.equals("louder")) {
                return volume(AudioManager.ADJUST_RAISE);
            }
            if (lower.contains("volume down") || lower.equals("quieter")) {
                return volume(AudioManager.ADJUST_LOWER);
            }
            if (lower.equals("mute") || lower.equals("mute volume")) {
                return volume(AudioManager.ADJUST_MUTE);
            }
            if (lower.equals("unmute") || lower.equals("unmute volume")) {
                return volume(AudioManager.ADJUST_UNMUTE);
            }

            if (lower.equals("pause") || lower.equals("pause music")) {
                return media(KeyEvent.KEYCODE_MEDIA_PAUSE);
            }
            if (lower.equals("play") || lower.equals("resume") || lower.equals("play music")) {
                return media(KeyEvent.KEYCODE_MEDIA_PLAY);
            }
            if (lower.equals("next") || lower.equals("next track") || lower.equals("skip")) {
                return media(KeyEvent.KEYCODE_MEDIA_NEXT);
            }
            if (lower.equals("previous") || lower.equals("previous track") || lower.equals("go back a track")) {
                return media(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }

            if (lower.equals("back") || lower.equals("go back")) {
                return JarvisAccessibilityService.back() ? "Done." : accessibilityRequired();
            }
            if (lower.equals("home") || lower.equals("go home")) {
                return JarvisAccessibilityService.home() ? "Done." : accessibilityRequired();
            }
            if (lower.startsWith("tap ")) {
                return JarvisAccessibilityService.clickText(raw.substring(4).trim())
                        ? "Done." : "I couldn’t find a visible control matching that.";
            }
            if (lower.startsWith("press ")) {
                return JarvisAccessibilityService.clickText(raw.substring(6).trim())
                        ? "Done." : "I couldn’t find a visible control matching that.";
            }
            if (lower.startsWith("type ")) {
                return JarvisAccessibilityService.typeText(raw.substring(5))
                        ? "Done." : "No editable field is focused.";
            }
            if (lower.startsWith("enter ")) {
                return JarvisAccessibilityService.typeText(raw.substring(6))
                        ? "Done." : "No editable field is focused.";
            }
            if (lower.contains("scroll down")) {
                return JarvisAccessibilityService.scrollForward()
                        ? "Done." : "Nothing scrollable was found.";
            }
            if (lower.contains("scroll up")) {
                return JarvisAccessibilityService.scrollBackward()
                        ? "Done." : "Nothing scrollable was found.";
            }
            if (lower.contains("what’s on my screen") || lower.contains("what's on my screen") ||
                    lower.contains("what is on my screen") || lower.equals("read screen")) {
                String screen = JarvisAccessibilityService.screenText();
                return screen.isEmpty() ? accessibilityRequired() : screen;
            }
        } catch (SecurityException error) {
            return "Android blocked that action because its permission is off.";
        } catch (Exception error) {
            return "That action failed: " +
                    (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
        return null;
    }

    private String call(String target) {
        String number = looksLikeNumber(target) ? target : phoneFor(target);
        if (number == null) {
            return hasPermission(Manifest.permission.READ_CONTACTS)
                    ? "I couldn’t find a phone number for " + target + "."
                    : "Enable Contacts permission so I can resolve that name.";
        }
        String label = looksLikeNumber(target) ? target : target;
        Uri uri = Uri.parse("tel:" + Uri.encode(number));
        if (hasPermission(Manifest.permission.CALL_PHONE)) {
            return launch(new Intent(Intent.ACTION_CALL, uri), "Calling " + label + ".");
        }
        return launch(new Intent(Intent.ACTION_DIAL, uri),
                "Call permission is off, so I opened the dialer for " + label + ".");
    }

    private String dial(String number) {
        return launch(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))),
                "Dialer opened.");
    }

    private String text(String specification) {
        MessageParts parts = MessageParts.parse(specification);
        String number = looksLikeNumber(parts.recipient) ? parts.recipient : phoneFor(parts.recipient);
        if (number == null) {
            return hasPermission(Manifest.permission.READ_CONTACTS)
                    ? "I couldn’t find " + parts.recipient + " in your contacts."
                    : "Enable Contacts permission so I can resolve that name.";
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)));
        intent.putExtra("sms_body", parts.body);
        return launch(intent, "Message ready for " + parts.recipient + ".");
    }

    private String email(String specification) {
        MessageParts parts = MessageParts.parse(specification);
        String address = parts.recipient.contains("@")
                ? parts.recipient
                : contactValue(
                        parts.recipient,
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        ContactsContract.CommonDataKinds.Email.ADDRESS);
        if (address == null) {
            return hasPermission(Manifest.permission.READ_CONTACTS)
                    ? "I couldn’t find an email address for " + parts.recipient + "."
                    : "Enable Contacts permission so I can resolve that name.";
        }
        String uri = "mailto:" + Uri.encode(address) +
                "?subject=" + Uri.encode(parts.subject) +
                "&body=" + Uri.encode(parts.body);
        return launch(new Intent(Intent.ACTION_SENDTO, Uri.parse(uri)),
                "Email ready for " + parts.recipient + ".");
    }

    private String calendarEvent(String specification) {
        EventParts event = EventParts.parse(specification);
        String attendeeEmail = event.attendee.isEmpty() ? null :
                contactValue(
                        event.attendee,
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        ContactsContract.CommonDataKinds.Email.ADDRESS);
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, event.title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end);
        if (attendeeEmail != null) intent.putExtra(Intent.EXTRA_EMAIL, new String[]{attendeeEmail});
        String result = launch(intent, "Calendar event ready" +
                (attendeeEmail == null ? "." : " with " + event.attendee + " invited."));
        if (!event.attendee.isEmpty() && attendeeEmail == null) {
            result += " I couldn’t resolve an email for " + event.attendee + ".";
        }
        return result;
    }

    private String navigate(String destination) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + Uri.encode(destination)));
        return launch(intent, "Opening directions to " + destination + ".");
    }

    private String search(String query) {
        Intent webSearch = new Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(SearchManager.QUERY, query);
        try {
            if (webSearch.resolveActivity(context.getPackageManager()) != null) {
                return launch(webSearch, "Searching for " + query + ".");
            }
        } catch (Exception ignored) {
        }
        return launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))),
                "Searching for " + query + ".");
    }

    private String openTarget(String target) {
        String lower = target.toLowerCase(Locale.ROOT);
        Intent direct = null;
        if (lower.equals("settings") || lower.contains("phone settings")) {
            direct = new Intent(Settings.ACTION_SETTINGS);
        } else if (lower.equals("accessibility") || lower.contains("accessibility settings")) {
            direct = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        } else if (lower.equals("notifications") || lower.contains("notification access")) {
            direct = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        } else if (lower.equals("camera")) {
            direct = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        }
        if (direct != null) return launch(direct, "Opened " + target + ".");

        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchers = context.getPackageManager().queryIntentActivities(query, 0);
        ResolveInfo partial = null;
        for (ResolveInfo info : launchers) {
            CharSequence labelValue = info.loadLabel(context.getPackageManager());
            String label = labelValue == null ? "" : labelValue.toString();
            if (label.equalsIgnoreCase(target)) {
                return launchPackage(info.activityInfo.packageName, target);
            }
            if (partial == null && label.toLowerCase(Locale.ROOT).contains(lower)) partial = info;
        }
        if (partial != null) return launchPackage(partial.activityInfo.packageName, target);
        return "I couldn’t find an installed app named " + target + ".";
    }

    private String launchPackage(String packageName, String label) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        return intent == null ? "I couldn’t launch " + label + "." : launch(intent, "Opened " + label + ".");
    }

    private String timer(String specification) {
        Matcher matcher = DURATION.matcher(specification);
        int totalSeconds = 0;
        while (matcher.find()) {
            int amount = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            if (unit.startsWith("day")) totalSeconds += amount * 86400;
            else if (unit.startsWith("hour")) totalSeconds += amount * 3600;
            else if (unit.startsWith("minute")) totalSeconds += amount * 60;
            else totalSeconds += amount;
        }
        if (totalSeconds <= 0) return "Tell me how long the timer should run.";
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS timer")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        return launch(intent, "Timer set.");
    }

    private String alarm(String specification) {
        int hour;
        int minute = 0;
        String lower = specification.toLowerCase(Locale.ROOT);
        if (lower.contains("noon")) {
            hour = 12;
        } else if (lower.contains("midnight")) {
            hour = 0;
        } else {
            Matcher matcher = CLOCK_TIME.matcher(lower);
            if (!matcher.find()) return "Tell me the alarm time.";
            hour = Integer.parseInt(matcher.group(1));
            minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            String amPm = matcher.group(3);
            if ("pm".equalsIgnoreCase(amPm) && hour < 12) hour += 12;
            if ("am".equalsIgnoreCase(amPm) && hour == 12) hour = 0;
        }
        if (hour > 23 || minute > 59) return "That isn’t a valid alarm time.";
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS alarm")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        return launch(intent, "Alarm set.");
    }

    private String torch(boolean enabled) throws Exception {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            return "Enable Camera permission so I can control the flashlight.";
        }
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        for (String cameraId : manager.getCameraIdList()) {
            Boolean flash = manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (Boolean.TRUE.equals(flash)) {
                manager.setTorchMode(cameraId, enabled);
                return enabled ? "Flashlight on." : "Flashlight off.";
            }
        }
        return "I couldn’t find a controllable flashlight.";
    }

    private String volume(int direction) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        manager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
        return "Done.";
    }

    private String media(int keyCode) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        manager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        manager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        return "Done.";
    }

    private String phoneFor(String name) {
        return contactValue(
                name,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                ContactsContract.CommonDataKinds.Phone.NUMBER);
    }

    private String contactValue(String name, Uri uri, String valueColumn) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null;
        String displayName = ContactsContract.Data.DISPLAY_NAME;
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{valueColumn, displayName},
                displayName + " LIKE ?",
                new String[]{"%" + name + "%"},
                displayName + " ASC")) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        return null;
    }

    private boolean hasPermission(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean looksLikeNumber(String value) {
        return value.matches("[+0-9() .-]{3,}");
    }

    private String launch(Intent intent, String success) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return success;
        } catch (SecurityException denied) {
            return "Android blocked that action because its permission is off.";
        } catch (Exception unavailable) {
            return "No compatible app is available for that action.";
        }
    }

    private String accessibilityRequired() {
        return "Enable JARVIS Device Control in Accessibility settings first.";
    }

    private static final class MessageParts {
        final String recipient;
        final String subject;
        final String body;

        MessageParts(String recipient, String subject, String body) {
            this.recipient = recipient;
            this.subject = subject;
            this.body = body;
        }

        static MessageParts parse(String raw) {
            String recipient = raw.trim();
            String subject = "";
            String body = "";
            int bodyIndex = indexOfAny(recipient.toLowerCase(Locale.ROOT), " saying ", " message ", " body ");
            if (bodyIndex >= 0) {
                String marker = markerAt(recipient.toLowerCase(Locale.ROOT), bodyIndex,
                        " saying ", " message ", " body ");
                body = recipient.substring(bodyIndex + marker.length()).trim();
                recipient = recipient.substring(0, bodyIndex).trim();
            }
            int subjectIndex = recipient.toLowerCase(Locale.ROOT).indexOf(" subject ");
            if (subjectIndex >= 0) {
                subject = recipient.substring(subjectIndex + 9).trim();
                recipient = recipient.substring(0, subjectIndex).trim();
            }
            if (recipient.toLowerCase(Locale.ROOT).startsWith("to ")) recipient = recipient.substring(3).trim();
            return new MessageParts(recipient, subject, body);
        }

        private static int indexOfAny(String value, String... markers) {
            int result = -1;
            for (String marker : markers) {
                int found = value.indexOf(marker);
                if (found >= 0 && (result < 0 || found < result)) result = found;
            }
            return result;
        }

        private static String markerAt(String value, int index, String... markers) {
            for (String marker : markers) if (value.startsWith(marker, index)) return marker;
            return "";
        }
    }

    private static final class EventParts {
        private static final Pattern ATTENDEE = Pattern.compile(
                "\\b(?:with|invite)\\s+([\\p{L}][\\p{L}' .-]*?)(?=\\s+(?:today|tomorrow|next|on|at|for|in)\\b|$)",
                Pattern.CASE_INSENSITIVE);
        private static final Pattern EVENT_DURATION = Pattern.compile(
                "\\bfor\\s+(\\d+)\\s*(minute|minutes|hour|hours)\\b",
                Pattern.CASE_INSENSITIVE);

        final String title;
        final String attendee;
        final long start;
        final long end;

        EventParts(String title, String attendee, long start, long end) {
            this.title = title;
            this.attendee = attendee;
            this.start = start;
            this.end = end;
        }

        static EventParts parse(String raw) {
            String lower = raw.toLowerCase(Locale.ROOT);
            String attendee = "";
            Matcher attendeeMatcher = ATTENDEE.matcher(raw);
            if (attendeeMatcher.find()) attendee = attendeeMatcher.group(1).trim();

            Calendar now = Calendar.getInstance();
            Calendar start = Calendar.getInstance();
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            boolean explicitDate = false;
            boolean explicitTime = false;

            Matcher relative = Pattern.compile(
                    "\\bin\\s+(\\d+)\\s*(minute|minutes|hour|hours|day|days)\\b",
                    Pattern.CASE_INSENSITIVE).matcher(lower);
            if (relative.find()) {
                int amount = Integer.parseInt(relative.group(1));
                String unit = relative.group(2);
                start = Calendar.getInstance();
                if (unit.startsWith("day")) start.add(Calendar.DAY_OF_YEAR, amount);
                else if (unit.startsWith("hour")) start.add(Calendar.HOUR_OF_DAY, amount);
                else start.add(Calendar.MINUTE, amount);
                explicitDate = true;
                explicitTime = true;
            } else {
                if (lower.contains("tomorrow")) {
                    start.add(Calendar.DAY_OF_YEAR, 1);
                    explicitDate = true;
                }
                int requestedDay = requestedDayOfWeek(lower);
                if (requestedDay > 0) {
                    int delta = (requestedDay - start.get(Calendar.DAY_OF_WEEK) + 7) % 7;
                    if (delta == 0 && (lower.contains("next ") || start.before(now))) delta = 7;
                    start.add(Calendar.DAY_OF_YEAR, delta);
                    explicitDate = true;
                }

                if (lower.contains("noon")) {
                    start.set(Calendar.HOUR_OF_DAY, 12);
                    start.set(Calendar.MINUTE, 0);
                    explicitTime = true;
                } else if (lower.contains("midnight")) {
                    start.set(Calendar.HOUR_OF_DAY, 0);
                    start.set(Calendar.MINUTE, 0);
                    explicitTime = true;
                } else {
                    Matcher time = Pattern.compile(
                            "\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b",
                            Pattern.CASE_INSENSITIVE).matcher(lower);
                    if (time.find()) {
                        int hour = Integer.parseInt(time.group(1));
                        int minute = time.group(2) == null ? 0 : Integer.parseInt(time.group(2));
                        String amPm = time.group(3);
                        if ("pm".equalsIgnoreCase(amPm) && hour < 12) hour += 12;
                        if ("am".equalsIgnoreCase(amPm) && hour == 12) hour = 0;
                        start.set(Calendar.HOUR_OF_DAY, hour);
                        start.set(Calendar.MINUTE, minute);
                        explicitTime = true;
                    }
                }
            }

            if (!explicitTime) {
                if (explicitDate) {
                    start.set(Calendar.HOUR_OF_DAY, 9);
                    start.set(Calendar.MINUTE, 0);
                } else {
                    start.add(Calendar.HOUR_OF_DAY, 1);
                    start.set(Calendar.MINUTE, 0);
                }
            } else if (!explicitDate && start.before(now)) {
                start.add(Calendar.DAY_OF_YEAR, 1);
            }

            long durationMs = 60 * 60 * 1000L;
            Matcher duration = EVENT_DURATION.matcher(lower);
            if (duration.find()) {
                int amount = Integer.parseInt(duration.group(1));
                durationMs = duration.group(2).startsWith("hour")
                        ? amount * 60L * 60L * 1000L
                        : amount * 60L * 1000L;
            }

            String title = raw
                    .replaceAll("(?i)\\b(today|tomorrow|next\\s+\\w+|on\\s+\\w+)\\b", "")
                    .replaceAll("(?i)\\b(?:at\\s+)?\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)\\b", "")
                    .replaceAll("(?i)\\b(?:at\\s+)?(?:noon|midnight)\\b", "")
                    .replaceAll("(?i)\\bin\\s+\\d+\\s*(?:minutes?|hours?|days?)\\b", "")
                    .replaceAll("(?i)\\bfor\\s+\\d+\\s*(?:minutes?|hours?)\\b", "")
                    .trim().replaceAll("\\s+", " ");
            if (!attendee.isEmpty()) {
                title = title.replaceFirst(
                        "(?i)\\b(?:with|invite)\\s+" + Pattern.quote(attendee) + "\\b", "").trim();
            }
            if (title.isEmpty()) title = "JARVIS event";
            return new EventParts(title, attendee, start.getTimeInMillis(), start.getTimeInMillis() + durationMs);
        }

        private static int requestedDayOfWeek(String lower) {
            String[] days = {"", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
            for (int i = 1; i < days.length; i++) if (lower.contains(days[i])) return i;
            return -1;
        }
    }
}
