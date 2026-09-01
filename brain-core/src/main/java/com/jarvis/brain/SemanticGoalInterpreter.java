package com.jarvis.brain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cheap semantic-reflex layer for obvious local capabilities; ambiguity falls through to reasoning. */
public final class SemanticGoalInterpreter {
    private static final Set<String> VAGUE_DESTINATIONS = Set.of(
            "there", "somewhere", "somewhere good", "dinner", "food", "a restaurant", "restaurant", "a place");
    private static final Pattern TIMER = Pattern.compile("\\b(?:set|start) (?:a )?timer(?: for)? (\\d+) (second|seconds|minute|minutes|hour|hours)\\b");
    // normalize() turns 7:30 into "7 30", so accept either punctuation-preserved or normalized time forms.
    private static final Pattern ALARM = Pattern.compile("\\b(?:set|make) (?:an )?alarm(?: for| at)? (\\d{1,2})(?:(?::| )(\\d{2}))?(?: (am|pm))?\\b");

    public Optional<Plan> interpret(String utterance) {
        String raw = utterance == null ? "" : utterance.trim();
        String lower = normalize(raw);
        if (lower.isEmpty()) return Optional.empty();

        if (isJarvisSettingsRequest(lower)) return Optional.of(new Plan("Open JARVIS settings",
                List.of(new PlanStep("open_jarvis_settings"))));

        if (isDialer(lower)) return Optional.of(new Plan("Open the phone dialer", List.of(new PlanStep("open_dialer"))));

        Matcher timer = TIMER.matcher(lower);
        if (startsAsRequest(lower, "set ", "start ") && timer.find()) return Optional.of(new Plan("Set timer", List.of(new PlanStep("set_timer",
                Map.of("amount", timer.group(1), "unit", timer.group(2)), false))));

        Matcher alarm = ALARM.matcher(lower);
        if (startsAsRequest(lower, "set ", "make ") && alarm.find()) {
            int hour = Integer.parseInt(alarm.group(1));
            int minute = alarm.group(2) == null ? 0 : Integer.parseInt(alarm.group(2));
            String meridiem = alarm.group(3);
            if ("pm".equals(meridiem) && hour < 12) hour += 12;
            if ("am".equals(meridiem) && hour == 12) hour = 0;
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return Optional.of(new Plan("Set alarm", List.of(new PlanStep("set_alarm",
                        Map.of("hour", String.valueOf(hour), "minute", String.valueOf(minute)), false))));
            }
        }

        String volumeAction = volumeAction(lower);
        if (volumeAction != null) return Optional.of(new Plan("Control volume",
                List.of(new PlanStep("volume_control", Map.of("action", volumeAction), false))));

        String mediaAction = mediaAction(lower);
        if (mediaAction != null) return Optional.of(new Plan("Control media",
                List.of(new PlanStep("media_control", Map.of("action", mediaAction), false))));

        String webQuery = webQuery(raw, lower);
        if (!webQuery.isBlank()) return Optional.of(new Plan("Search the web",
                List.of(new PlanStep("web_search", Map.of("query", webQuery), false))));

        String calendarWhen = calendarWhen(lower);
        if (calendarWhen != null) return Optional.of(new Plan("Read calendar agenda",
                List.of(new PlanStep("calendar_query", Map.of("when", calendarWhen), false))));

        String destination = navigationDestination(raw, lower);
        if (!destination.isBlank()) return Optional.of(new Plan("Navigate to destination",
                List.of(new PlanStep("navigate", Map.of("destination", destination), false))));

        if ((lower.contains("torch") || lower.contains("flashlight"))
                && startsAsRequest(lower, "turn ", "switch ", "enable ", "disable ", "kill ", "flashlight ", "torch ")) {
            String state = offLanguage(lower) ? "off" : onLanguage(lower) ? "on" : "";
            if (!state.isBlank()) return Optional.of(new Plan("Set flashlight",
                    List.of(new PlanStep("set_flashlight", Map.of("state", state), false))));
        }

        String app = openedApp(raw, lower);
        if (!app.isBlank()) return Optional.of(new Plan("Open " + app,
                List.of(new PlanStep("open_app", Map.of("app", app), false))));

        if (isFoodDiscovery(lower)) {
            Map<String,String> args = Map.of(
                    "category", "restaurant",
                    "meal", lower.contains("dinner") ? "dinner" : "food",
                    "time", lower.contains("tonight") ? "tonight" : "now",
                    "preference_context", raw);
            return Optional.of(new Plan("Find a good nearby place to eat",
                    List.of(new PlanStep("discover_places", args, false),
                            new PlanStep("rank_options", Map.of("use_personal_context", "true"), false),
                            new PlanStep("present_options", Map.of("count", "3"), false))));
        }
        return Optional.empty();
    }

    private static boolean isJarvisSettingsRequest(String lower) {
        if (!lower.contains("settings")) return false;
        return startsAsRequest(lower,
                "settings", "open settings", "open the settings", "open jarvis settings",
                "show settings", "show me settings", "show jarvis settings", "go to settings");
    }

    private static boolean isDialer(String lower) {
        return startsAsRequest(lower,
                "open phone", "open the phone", "open my phone", "open dialer", "open the dialer",
                "phone app", "phone", "dialer", "make a phone call", "make calls", "dial ");
    }

    private static String openedApp(String raw, String lower) {
        if (!startsAsRequest(lower, "open ", "launch ")) return "";
        String[] cues = {"open ", "launch "};
        for (String cue : cues) {
            int idx = lower.indexOf(cue);
            if (idx < 0) continue;
            String candidate = raw.substring(Math.min(raw.length(), idx + cue.length())).trim();
            candidate = candidate.replaceFirst("(?i)[,!.?]*\\s+please[.!?]*$", "").trim();
            if (candidate.isBlank()) return "";
            String normalized = normalize(candidate);
            if (normalized.equals("settings") || normalized.equals("the settings") || normalized.contains("phone dialer")) return "";
            if (looksLikeDescriptiveClause(normalized)) return "";
            return candidate;
        }
        return "";
    }

    private static boolean looksLikeDescriptiveClause(String normalized) {
        return normalized.matches(".*\\b(?:about|because|when|while|if|that|which|who|where|why|is|are|was|were|can|could|should|would)\\b.*");
    }

    private static String webQuery(String raw, String lower) {
        String[] cues = {"search the web for ", "search web for ", "search online for ", "look up online "};
        if (!startsAsRequest(lower, cues)) return "";
        for (String cue : cues) {
            int idx = lower.indexOf(cue);
            if (idx >= 0) return raw.substring(Math.min(raw.length(), idx + cue.length())).trim().replaceFirst("[.!?]+$", "").trim();
        }
        return "";
    }

    private static String volumeAction(String lower) {
        if (!lower.contains("volume") && !lower.contains("sound")) return null;
        if (!startsAsRequest(lower, "turn ", "lower ", "raise ", "mute ", "unmute ", "make ", "volume ", "sound ")) return null;
        if (lower.contains("unmute")) return "unmute";
        if (lower.contains("mute")) return "mute";
        if (lower.contains("down") || lower.contains("lower") || lower.contains("quieter")) return "down";
        if (lower.contains("up") || lower.contains("raise") || lower.contains("louder")) return "up";
        return null;
    }

    private static boolean startsAsRequest(String lower, String... commandPrefixes) {
        String value = lower;
        String[] polite = {"please ", "can you ", "could you ", "would you ", "will you ", "jarvis ",
                "i want you to ", "i need you to "};
        boolean changed;
        do {
            changed = false;
            for (String prefix : polite) {
                if (value.startsWith(prefix)) {
                    value = value.substring(prefix.length()).trim();
                    changed = true;
                    break;
                }
            }
        } while (changed && !value.isEmpty());
        for (String prefix : commandPrefixes) if (value.startsWith(prefix)) return true;
        return false;
    }

    private static String mediaAction(String lower) {
        boolean media = lower.contains("music") || lower.contains("media") || lower.contains("track") || lower.contains("song");
        if (!media) return null;
        if (!startsAsRequest(lower, "play ", "pause ", "resume ", "continue ", "next ", "previous ", "skip ", "back ", "go back ")) return null;
        if (lower.contains("pause")) return "pause";
        if (lower.contains("resume") || lower.contains("continue") || lower.contains("play")) return "play";
        if (lower.contains("next") || lower.contains("skip")) return "next";
        if (lower.contains("previous") || lower.contains("back")) return "previous";
        return null;
    }

    private static String calendarWhen(String lower) {
        boolean agendaConcept = lower.contains("calendar") || lower.contains("schedule") || lower.contains("agenda")
                || lower.contains("my day") || lower.contains("day look like");
        if (!agendaConcept) return null;
        boolean explicitRequest = startsAsRequest(lower,
                "show ", "check ", "read ", "calendar ", "schedule ", "agenda ", "tell me ",
                "what does my day", "what s on ", "what is on ");
        if (!explicitRequest) return null;
        if (lower.contains("tomorrow")) return "tomorrow";
        if (lower.contains("today") || lower.contains("my day")) return "today";
        return null;
    }

    private static String navigationDestination(String raw, String lower) {
        String[] cues = {"get me to ", "take me to ", "navigate to ", "directions to ", "route me to ", "give me directions to "};
        if (!startsAsRequest(lower, cues)) return "";
        for (String cue : cues) {
            int idx = lower.indexOf(cue);
            if (idx < 0) continue;
            String candidate = raw.substring(Math.min(raw.length(), idx + cue.length())).trim();
            String normalizedCandidate = normalize(candidate);
            if (normalizedCandidate.isBlank() || VAGUE_DESTINATIONS.contains(normalizedCandidate)) return "";
            return candidate;
        }
        return "";
    }

    private static boolean offLanguage(String lower) {
        return lower.contains("turn off") || lower.contains("shut off") || lower.contains("switch off")
                || lower.contains("kill the") || lower.contains("disable");
    }
    private static boolean onLanguage(String lower) { return lower.contains("turn on") || lower.contains("switch on") || lower.contains("enable"); }
    private static boolean isFoodDiscovery(String lower) {
        boolean food = lower.contains("food") || lower.contains("eat") || lower.contains("dinner") || lower.contains("restaurant");
        boolean discovery = lower.contains("find") || lower.contains("somewhere") || lower.contains("place") || lower.contains("where should");
        boolean context = lower.contains("tonight") || lower.contains("nearby") || lower.contains("around here") || lower.contains("dinner");
        return food && discovery && context;
    }
    private static String normalize(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }
}
