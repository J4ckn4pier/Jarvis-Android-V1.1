package com.jarvis.mobile.brain.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jarvis.mobile.brain.core.IntentPlan.Intent;

/** Fast, offline first-pass language router. Consequential work is proposed, not performed. */
public final class LocalIntentEngine {
    private static final Pattern PHONE = Pattern.compile("[+0-9][0-9 ()-]{5,}");

    public IntentPlan plan(String raw) {
        String q = CommandNormalizer.normalize(raw);
        if (q.isEmpty()) return p(Intent.UNKNOWN, "", "", "I’m listening, sir.", "jarvis_at_service", .0);

        // Specific negatives and prefixes must precede their shorter collisions.
        if (one(q, "unmute", "unmute the phone", "turn the sound back on", "sound on"))
            return p(Intent.UNMUTE, "", "unmute", "Sound restored, sir.", "right_away_sir", .99);
        if (one(q, "mute", "mute the phone", "turn the sound off", "sound off"))
            return p(Intent.MUTE, "", "mute", "Muting, sir.", "right_away_sir", .99);
        if (one(q, "help", "help me", "what can you do", "show commands", "commands", "capabilities"))
            return p(Intent.HELP, "", "help", "At your service, sir. I can manage calls, messages, email, calendar, navigation, alarms, timers, apps, search, media, device controls, memories, tasks, and notifications.", "what_can_i_do", .99);
        if (one(q, "hello", "hi", "good morning", "good afternoon", "good evening", "you there"))
            return p(Intent.GREETING, "", "", "At your service, sir.", "hello_sir", .98);
        if (q.contains("who are you") || q.contains("what are you") || q.equals("your name"))
            return p(Intent.IDENTITY, "", "", "I’m JARVIS, sir—your executive interface and device operator.", "jarvis_at_service", .98);
        if (q.equals("thanks") || q.equals("thank you") || q.equals("thank you jarvis"))
            return p(Intent.THANKS, "", "", "Of course, sir.", "for_anything_sir", .98);

        if (q.startsWith("call ")) return contact(Intent.CALL, q.substring(5), "call ");
        if (q.startsWith("phone ")) return contact(Intent.CALL, q.substring(6), "call ");
        if (q.startsWith("ring ")) return contact(Intent.CALL, q.substring(5), "call ");
        if (q.startsWith("dial ")) return contact(Intent.DIAL, q.substring(5), "dial ");
        Matcher number = PHONE.matcher(q);
        if ((q.startsWith("call number ") || q.startsWith("dial number ")) && number.find())
            return p(Intent.DIAL, number.group(), "dial " + number.group(), "Opening the dialer, sir.", "right_away_sir", .98);

        if (starts(q, "text ", "message ", "send a text to ", "send an sms to ")) {
            String value = after(q, "send an sms to ", "send a text to ", "message ", "text ");
            return p(Intent.SMS, value, "text " + value, "Preparing the message, sir.", "right_away_sir", .96);
        }
        if (starts(q, "email ", "send an email to ", "compose an email to ")) {
            String value = after(q, "compose an email to ", "send an email to ", "email ");
            return p(Intent.EMAIL, value, "email " + value, "Preparing the email, sir.", "right_away_sir", .96);
        }
        if (starts(q, "create calendar event ", "add calendar event ", "schedule ", "calendar ", "invite ")) {
            String value = after(q, "create calendar event ", "add calendar event ", "schedule ", "calendar ", "invite ");
            return p(Intent.CALENDAR, value, "calendar " + value, "Opening the calendar, sir.", "right_away_sir", .94);
        }
        if (starts(q, "navigate to ", "directions to ", "take me to ", "map ", "go to ")) {
            String value = after(q, "directions to ", "navigate to ", "take me to ", "map ", "go to ");
            return p(Intent.NAVIGATE, value, "navigate " + value, "Plotting the route, sir.", "right_away_sir", .96);
        }
        if (q.startsWith("open ") || q.startsWith("launch ") || q.startsWith("start app ")) {
            String value = after(q, "start app ", "launch ", "open ");
            if (value.endsWith(" on my computer") || value.endsWith(" on the pc") || value.endsWith(" on windows")) return IntentPlan.unknown();
            return p(Intent.OPEN_APP, value, "open " + value, "Opening " + value + ", sir.", "right_away_sir", .94);
        }
        if (starts(q, "search for ", "search the web for ", "look up ", "google ")) {
            String value = after(q, "search the web for ", "search for ", "look up ", "google ");
            return p(Intent.WEB_SEARCH, value, "search " + value, "Searching, sir.", "right_away_sir", .94);
        }

        if (starts(q, "set a timer for ", "timer for ", "start a timer for ", "timer ")) {
            String value = after(q, "start a timer for ", "set a timer for ", "timer for ", "timer ");
            return p(Intent.TIMER, value, "timer " + value, "Timer prepared, sir.", "right_away_sir", .97);
        }
        if (starts(q, "set an alarm for ", "alarm for ", "wake me at ", "alarm ")) {
            String value = after(q, "set an alarm for ", "wake me at ", "alarm for ", "alarm ");
            return p(Intent.ALARM, value, "alarm " + value, "Alarm prepared, sir.", "right_away_sir", .97);
        }
        if (one(q, "flashlight on", "turn on the flashlight", "torch on", "turn the torch on")) return simple(Intent.FLASHLIGHT_ON, "flashlight on", "Flashlight on, sir.");
        if (one(q, "flashlight off", "turn off the flashlight", "torch off", "turn the torch off")) return simple(Intent.FLASHLIGHT_OFF, "flashlight off", "Flashlight off, sir.");
        if (one(q, "volume up", "turn it up", "increase volume", "louder")) return simple(Intent.VOLUME_UP, "volume up", "Increasing volume, sir.");
        if (one(q, "volume down", "turn it down", "decrease volume", "quieter")) return simple(Intent.VOLUME_DOWN, "volume down", "Decreasing volume, sir.");
        if (one(q, "play", "resume", "resume music", "play music")) return simple(Intent.MEDIA_PLAY, "play", "Resuming, sir.");
        if (one(q, "pause", "pause music", "stop music")) return simple(Intent.MEDIA_PAUSE, "pause", "Paused, sir.");
        if (one(q, "next", "next song", "next track", "skip")) return simple(Intent.MEDIA_NEXT, "next", "Next track, sir.");
        if (one(q, "previous", "previous song", "previous track", "go back a track")) return simple(Intent.MEDIA_PREVIOUS, "previous", "Previous track, sir.");
        if (starts(q, "tap ", "type ", "scroll ") || one(q, "go back", "go home", "read the screen")) return p(Intent.ACCESSIBILITY, q, q, "Understood, sir.", "right_away_sir", .90);

        if (one(q, "notifications", "read notifications", "what are my notifications", "what did i miss")) return simple(Intent.NOTIFICATIONS, "notifications", "Checking notifications, sir.");
        if (q.startsWith("remember ")) return p(Intent.REMEMBER, q.substring(9), q, "I’ll remember that, sir.", "right_away_sir", .98);
        if (q.startsWith("recall ")) return p(Intent.RECALL, q.substring(7), q, "Checking memory, sir.", "right_away_sir", .98);
        if (q.startsWith("what do you remember about ")) return p(Intent.RECALL, q.substring(27), "recall " + q.substring(27), "Checking memory, sir.", "right_away_sir", .98);
        if (one(q, "recall", "what do you remember")) return simple(Intent.RECALL, "recall", "Checking memory, sir.");
        if (starts(q, "add task ", "create task ", "remind me to ")) {
            String value = after(q, "remind me to ", "create task ", "add task ");
            return p(Intent.ADD_TASK, value, "add task " + value, "Adding the task, sir.", "right_away_sir", .97);
        }
        if (one(q, "tasks", "list tasks", "what are my tasks", "what do i need to do")) return simple(Intent.LIST_TASKS, "tasks", "Checking your tasks, sir.");
        if (starts(q, "complete task ", "finish task ")) {
            String value = after(q, "complete task ", "finish task ");
            return p(Intent.COMPLETE_TASK, value, "complete task " + value, "Completing the task, sir.", "right_away_sir", .97);
        }
        if (one(q, "what time is it", "time", "current time")) return simple(Intent.TIME, "time", "Checking the time, sir.");
        if (one(q, "what is the date", "what's the date", "date", "today's date", "what day is it")) return simple(Intent.DATE, "date", "Checking the date, sir.");
        if (one(q, "battery", "battery level", "how much battery is left", "what's my battery")) return simple(Intent.BATTERY, "battery", "Checking battery status, sir.");

        if (q.startsWith("weather") || q.contains("weather in ") || q.startsWith("news") || q.startsWith("latest news"))
            return p(Intent.KNOWLEDGE_QUERY, q, "knowledge " + q, "I’ll check that for you, sir.", "one_moment_sir", .86);
        if (q.matches("^(who|what|when|where|why|how|which)\\b.*") || q.endsWith("?"))
            return p(Intent.KNOWLEDGE_QUERY, q, "knowledge " + q, "I’ll look into that, sir.", "one_moment_sir", .72);
        return IntentPlan.unknown();
    }

    public IntentPlan planCandidates(List<String> candidates) {
        IntentPlan best = IntentPlan.unknown();
        if (candidates == null) return best;
        for (String candidate : candidates) {
            IntentPlan current = plan(candidate);
            if (current.confidence() > best.confidence()) best = current;
        }
        return best;
    }

    private static IntentPlan contact(Intent intent, String value, String prefix) {
        value = value.trim();
        return p(intent, value, prefix + value, "Calling " + value + ", sir.", "right_away_sir", .97);
    }
    private static IntentPlan simple(Intent intent, String command, String answer) { return p(intent, "", command, answer, "right_away_sir", .98); }
    private static IntentPlan p(Intent intent, String payload, String command, String answer, String cue, double confidence) {
        return new IntentPlan(intent, payload, command, answer, cue, confidence);
    }
    private static boolean one(String q, String... values) { for (String v : values) if (q.equals(v)) return true; return false; }
    private static boolean starts(String q, String... values) { for (String v : values) if (q.startsWith(v)) return true; return false; }
    private static String after(String q, String... prefixes) { for (String p : prefixes) if (q.startsWith(p)) return q.substring(p.length()).trim(); return ""; }
}
