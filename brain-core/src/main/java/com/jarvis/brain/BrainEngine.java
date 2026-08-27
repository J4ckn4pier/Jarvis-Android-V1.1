package com.jarvis.brain;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrainEngine {
    private static final Pattern WAKE = Pattern.compile("(?i)^\\s*(?:hey\\s+)?jarvis[,:]?\\s*");
    private static final Pattern CALL_RESERVATION = Pattern.compile(
            "(?i)call\\s+(.+?)(?:\\s+in\\s+(.+?))?\\s+and\\s+tell\\s+them\\s+i\\s+would\\s+like\\s+a\\s+reservation\\s+for\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?");

    private final ConversationSession session;

    private BrainEngine(Clock clock) { this.session = new ConversationSession(clock); }

    public static BrainEngine createDefault(Clock clock) { return new BrainEngine(clock); }

    public BrainResponse handle(String raw) {
        String input = raw == null ? "" : raw.trim();
        Matcher wake = WAKE.matcher(input);
        boolean hadWake = wake.find();
        if (hadWake) {
            session.wake();
            input = input.substring(wake.end()).trim();
        }

        if (!session.isActive()) {
            return BrainResponse.of(BrainResponse.Kind.IGNORED_AMBIENT, "", null, false, false, session.snapshot());
        }

        boolean acceptedWithoutWake = !hadWake;
        session.touch();
        if (input.isBlank()) {
            return BrainResponse.of(BrainResponse.Kind.CONVERSATION, "I'm here.", null, true, acceptedWithoutWake, session.snapshot());
        }

        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.matches("(?:go to )?sleep|stop listening|that's all|that is all")) {
            session.sleep();
            return BrainResponse.of(BrainResponse.Kind.CONVERSATION, "Of course.", null, false, acceptedWithoutWake, session.snapshot());
        }

        session.rememberTurn(input);
        String context = session.snapshot();

        if (lower.contains("how are you")) {
            return BrainResponse.of(BrainResponse.Kind.CONVERSATION,
                    "I'm doing well. How are you?", null, true, acceptedWithoutWake, context);
        }

        if (isDialerAlias(lower)) {
            Plan plan = new Plan("Open the phone dialer", List.of(new PlanStep("open_dialer")));
            return BrainResponse.of(BrainResponse.Kind.ACTION_PLAN, "Opening the phone app.", plan, true, acceptedWithoutWake, context);
        }

        Matcher call = CALL_RESERVATION.matcher(input);
        if (call.find()) {
            String business = call.group(1).trim();
            String location = call.group(2) == null ? "" : call.group(2).trim();
            String time = normalizeTime(call.group(3), call.group(4), call.group(5));
            Map<String, String> args = Map.of(
                    "business", business,
                    "location", location,
                    "goal", "Request a restaurant reservation",
                    "preferred_time", time,
                    "fallback_policy", "Ask for nearby available times if preferred time is unavailable"
            );
            List<PlanStep> steps = new ArrayList<>();
            steps.add(new PlanStep("resolve_business", Map.of("business", business, "location", location), false));
            steps.add(new PlanStep("place_conversational_call", args, true));
            steps.add(new PlanStep("report_outcome", Map.of("include_alternatives", "true"), false));
            Plan plan = new Plan("Book a restaurant reservation by phone", List.copyOf(steps));
            return BrainResponse.of(BrainResponse.Kind.ACTION_PLAN,
                    "I can resolve the restaurant and prepare the reservation call. I'll ask for approval before I speak to them on your behalf.",
                    plan, true, acceptedWithoutWake, context);
        }

        if (isDinnerDiscovery(lower, context.toLowerCase(Locale.ROOT))) {
            Map<String, String> discovery = Map.of(
                    "category", "restaurant",
                    "meal", "dinner",
                    "time", "tonight",
                    "preference_context", context
            );
            Plan plan = new Plan("Find a good place for dinner tonight", List.of(
                    new PlanStep("discover_places", discovery, false),
                    new PlanStep("rank_options", Map.of("use_personal_context", "true"), false),
                    new PlanStep("present_options", Map.of("count", "3"), false)
            ));
            return BrainResponse.of(BrainResponse.Kind.ACTION_PLAN,
                    "I'll look for dinner options and rank the best matches.", plan, true, acceptedWithoutWake, context);
        }

        if (isConversationalFollowup(lower)) {
            return BrainResponse.of(BrainResponse.Kind.CONVERSATION,
                    "I'm with you. Tell me more.", null, true, acceptedWithoutWake, context);
        }

        return BrainResponse.of(BrainResponse.Kind.REASONING_REQUIRED,
                "I'll reason through that and work out the best next step.", null, true, acceptedWithoutWake, context);
    }

    private static boolean isDialerAlias(String lower) {
        return lower.matches("(?:open\\s+)?(?:the\\s+)?(?:phone(?:\\s+app)?|dialer|calls?|telephone)");
    }

    private static boolean isDinnerDiscovery(String lower, String context) {
        if (lower.contains("find me a place to eat") || lower.contains("find me somewhere good") ||
                lower.contains("where should i eat") || lower.contains("dinner tonight")) return true;
        return lower.contains("find me somewhere") && (context.contains("food") || context.contains("italian") || context.contains("dinner"));
    }

    private static boolean isConversationalFollowup(String lower) {
        return lower.startsWith("what have you") || lower.startsWith("how about you") ||
                lower.startsWith("why do you") || lower.startsWith("tell me more") ||
                lower.startsWith("and what") || lower.startsWith("what do you think");
    }

    private static String normalizeTime(String hourRaw, String minuteRaw, String meridiemRaw) {
        int hour = Integer.parseInt(hourRaw);
        int minute = minuteRaw == null ? 0 : Integer.parseInt(minuteRaw);
        String meridiem = meridiemRaw == null ? (hour <= 7 ? "PM" : "AM") : meridiemRaw.toUpperCase(Locale.ROOT);
        return String.format(Locale.ROOT, "%d:%02d %s", hour, minute, meridiem);
    }
}
