package com.jarvis.brain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Cheap semantic-reflex layer for obvious local capabilities; ambiguity falls through to reasoning. */
public final class SemanticGoalInterpreter {
    private static final Set<String> VAGUE_DESTINATIONS = Set.of(
            "there", "somewhere", "somewhere good", "dinner", "food", "a restaurant", "restaurant", "a place");

    public Optional<Plan> interpret(String utterance) {
        String raw = utterance == null ? "" : utterance.trim();
        String lower = normalize(raw);
        if (lower.isEmpty()) return Optional.empty();

        if (isDialer(lower)) return Optional.of(new Plan("Open the phone dialer", List.of(new PlanStep("open_dialer"))));

        String calendarWhen = calendarWhen(lower);
        if (calendarWhen != null) return Optional.of(new Plan("Read calendar agenda",
                List.of(new PlanStep("calendar_query", Map.of("when", calendarWhen), false))));

        String destination = navigationDestination(raw, lower);
        if (!destination.isBlank()) return Optional.of(new Plan("Navigate to destination",
                List.of(new PlanStep("navigate", Map.of("destination", destination), false))));

        if (lower.contains("torch") || lower.contains("flashlight")) {
            String state = offLanguage(lower) ? "off" : onLanguage(lower) ? "on" : "";
            if (!state.isBlank()) return Optional.of(new Plan("Set flashlight",
                    List.of(new PlanStep("set_flashlight", Map.of("state", state), false))));
        }

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

    private static boolean isDialer(String lower) {
        if (!(lower.contains("phone") || lower.contains("call") || lower.contains("dial"))) return false;
        return lower.contains("open") || lower.contains("use to make") || lower.contains("make a phone call")
                || lower.contains("make calls") || lower.contains("dialer");
    }

    private static String calendarWhen(String lower) {
        boolean agendaConcept = lower.contains("calendar") || lower.contains("schedule") || lower.contains("agenda")
                || lower.contains("my day") || lower.contains("day look like");
        if (!agendaConcept) return null;
        if (lower.contains("tomorrow")) return "tomorrow";
        if (lower.contains("today") || lower.contains("my day")) return "today";
        return null;
    }

    private static String navigationDestination(String raw, String lower) {
        String[] cues = {"get me to ", "take me to ", "navigate to ", "directions to ", "route me to "};
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
