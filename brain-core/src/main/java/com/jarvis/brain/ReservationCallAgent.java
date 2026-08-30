package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReservationCallAgent {
    private static final Pattern TIME = Pattern.compile("(?i)\\b(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*(am|pm)?\\b");
    private final String guestName;
    private final String preferredTime;
    private CallOutcome outcome = new CallOutcome(CallOutcome.Status.IN_PROGRESS, "", List.of(), "");

    public ReservationCallAgent(String guestName, String preferredTime) {
        this.guestName = guestName;
        this.preferredTime = preferredTime;
    }

    public String openingLine(String business) {
        return "Hello, I'm calling on behalf of " + guestName + ". I'd like to request a reservation at " + business + " for " + preferredTime + ". Is that available?";
    }

    public String onRemoteSpeech(String speech) {
        String lower = speech == null ? "" : speech.toLowerCase(Locale.ROOT);
        if (indicatesPreferredAvailable(lower)) {
            outcome = new CallOutcome(CallOutcome.Status.CONFIRMED, preferredTime, List.of(), "Reservation confirmed for " + preferredTime + ".");
            return "Yes, please confirm the reservation for " + preferredTime + " under " + guestName + ". Thank you.";
        }
        if (indicatesUnavailable(lower)) {
            List<String> alternatives = extractTimes(speech);
            outcome = new CallOutcome(CallOutcome.Status.ALTERNATIVES_AVAILABLE, "", alternatives, "Preferred time unavailable; alternatives collected.");
            return "Thank you. I'll pass those available times along and have them decide before booking another time.";
        }
        return "Could you tell me whether " + preferredTime + " is available, and if not, what nearby times you have?";
    }

    public CallOutcome outcome() { return outcome; }

    private boolean indicatesPreferredAvailable(String lower) {
        return (lower.contains("can do") || lower.contains("available") || lower.contains("we have")) && !indicatesUnavailable(lower) && (lower.contains("five") || lower.contains("5") || lower.contains(preferredTime.toLowerCase(Locale.ROOT)));
    }

    private boolean indicatesUnavailable(String lower) {
        return lower.contains("booked") || lower.contains("not available") || lower.contains("can't do") || lower.contains("cannot do") || lower.contains("unavailable");
    }

    private static List<String> extractTimes(String speech) {
        List<String> times = new ArrayList<>();
        Matcher matcher = TIME.matcher(speech == null ? "" : speech);
        while (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            String minute = matcher.group(2) == null ? "00" : matcher.group(2);
            String meridiem = matcher.group(3) == null ? "PM" : matcher.group(3).toUpperCase(Locale.ROOT);
            String formatted = hour + ":" + minute + " " + meridiem;
            if (!times.contains(formatted)) times.add(formatted);
        }
        return List.copyOf(times);
    }
}
