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
    private static final Pattern CALL_RESERVATION = Pattern.compile("(?i)call\\s+(.+?)(?:\\s+in\\s+(.+?))?\\s+and\\s+tell\\s+them\\s+i\\s+would\\s+like\\s+a\\s+reservation\\s+for\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?");

    private final ConversationSession session;
    private final java.util.Map<String, String> workingMemory = new java.util.HashMap<>();
    private String lastWeatherLocation = "";

    private BrainEngine(Clock clock) { this.session = new ConversationSession(clock); }
    public static BrainEngine createDefault(Clock clock) { return new BrainEngine(clock); }

    /** Marks an explicitly opened assistant surface as directed conversation without fabricating a wake utterance. */
    public void beginInvokedConversation() { session.wake(); }
    boolean isConversationActive() { return session.isActive(); }

    public BrainResponse handle(String raw) {
        String input = raw == null ? "" : raw.trim();
        Matcher wake = WAKE.matcher(input);
        boolean hadWake = wake.find();
        if (hadWake) { session.wake(); input = input.substring(wake.end()).trim(); }
        if (!session.isActive()) return BrainResponse.of(BrainResponse.Kind.IGNORED_AMBIENT, "", null, false, false, session.snapshot());
        boolean acceptedWithoutWake = !hadWake;
        session.touch();
        if (input.isBlank()) return BrainResponse.of(BrainResponse.Kind.CONVERSATION, "I'm here.", null, true, acceptedWithoutWake, session.snapshot());
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.matches("(?:go to )?sleep|stop listening|that's all|that is all")) {
            session.sleep();
            return BrainResponse.of(BrainResponse.Kind.CONVERSATION, "Of course.", null, false, acceptedWithoutWake, session.snapshot());
        }
        session.rememberTurn(input);
        String context = session.snapshot();
        if (lower.contains("how are you")) return BrainResponse.of(BrainResponse.Kind.CONVERSATION, "I'm doing well. How are you?", null, true, acceptedWithoutWake, context);
        if (isHelpRequest(lower)) return BrainResponse.of(BrainResponse.Kind.CONVERSATION,
                "You can speak naturally. I can call contacts, send messages, set reminders and timers, check your calendar, weather and notifications, navigate, control media and device features, research, plan, and remember useful context. I can prepare consequential actions, but I require your approval before I communicate, call, book, spend, or make destructive changes on your behalf.",
                null, true, acceptedWithoutWake, context);

        BrainResponse memoryResponse = handleMemory(input, lower, acceptedWithoutWake, context);
        if (memoryResponse != null) return memoryResponse;
        BrainResponse mathResponse = handleMath(lower, acceptedWithoutWake, context);
        if (mathResponse != null) return mathResponse;
        BrainResponse assistantTool = handleCommonAssistantTools(input, lower, acceptedWithoutWake, context);
        if (assistantTool != null) return assistantTool;

        if (isDialerAlias(lower)) {
            Plan plan = new Plan("Open the phone dialer", List.of(new PlanStep("open_dialer")));
            return BrainResponse.of(BrainResponse.Kind.ACTION_PLAN, "Opening the phone app.", plan, true, acceptedWithoutWake, context);
        }

        Matcher call = CALL_RESERVATION.matcher(input);
        if (call.find()) {
            String business = call.group(1).trim();
            String location = call.group(2) == null ? "" : call.group(2).trim();
            String time = normalizeTime(call.group(3), call.group(4), call.group(5));
            String place = location.isBlank() ? business : business + " in " + location;
            return BrainResponse.of(BrainResponse.Kind.REASONING_REQUIRED,
                    "I need to resolve the exact phone destination and who I'm representing before I can prepare a call to " + place + " for " + time + ". I won't guess those details.",
                    null, true, acceptedWithoutWake, context);
        }

        Matcher contactCall = Pattern.compile("(?i)^call\\s+(.+)$").matcher(input);
        if (contactCall.matches() && isHighConfidenceCallRecipient(contactCall.group(1))) {
            return action("Call contact", "call_contact", Map.of("recipient", contactCall.group(1).trim()), true,
                    acceptedWithoutWake, context);
        }

        if (isDinnerDiscovery(lower, context.toLowerCase(Locale.ROOT))) {
            Map<String, String> discovery = Map.of("category", "restaurant", "meal", "dinner", "time", "tonight", "preference_context", context);
            Plan plan = new Plan("Find a good place for dinner tonight", List.of(new PlanStep("discover_places", discovery, false), new PlanStep("rank_options", Map.of("use_personal_context", "true"), false), new PlanStep("present_options", Map.of("count", "3"), false)));
            return BrainResponse.of(BrainResponse.Kind.ACTION_PLAN, "I'll look for dinner options and rank the best matches.", plan, true, acceptedWithoutWake, context);
        }

        return BrainResponse.of(BrainResponse.Kind.REASONING_REQUIRED, "I'll reason through that and work out the best next step.", null, true, acceptedWithoutWake, context);
    }

    private BrainResponse handleMemory(String input, String lower, boolean acceptedWithoutWake, String context) {
        if (lower.startsWith("remember ")) {
            String statement = input.substring("remember ".length()).trim();
            Matcher m = Pattern.compile("(?i)^(?:that\\s+)?(.+?)\\s+is\\s+(.+)$").matcher(statement);
            if (m.matches()) {
                workingMemory.put(normalizeMemoryKey(m.group(1)), m.group(2).trim());
                return BrainResponse.of(BrainResponse.Kind.CONVERSATION, "I'll remember that.", null, true, acceptedWithoutWake, context);
            }
        }
        Matcher q = Pattern.compile("(?i)^what(?:'s| is)\\s+my\\s+(.+?)[?]?$", Pattern.CASE_INSENSITIVE).matcher(input);
        if (q.matches()) {
            String value = workingMemory.get(normalizeMemoryKey("my " + q.group(1)));
            if (value != null) return BrainResponse.of(BrainResponse.Kind.CONVERSATION, "Your " + q.group(1).trim() + " is " + value + ".", null, true, acceptedWithoutWake, context);
        }
        return null;
    }

    private static String normalizeMemoryKey(String raw) { return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }

    private BrainResponse handleMath(String lower, boolean acceptedWithoutWake, String context) {
        Matcher m = Pattern.compile("(?:what is|what's)?\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:times|x|multiplied by)\\s*(-?\\d+(?:\\.\\d+)?)").matcher(lower);
        if (m.matches()) {
            double value = Double.parseDouble(m.group(1)) * Double.parseDouble(m.group(2));
            String answer = value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
            return BrainResponse.of(BrainResponse.Kind.CONVERSATION, answer + ".", null, true, acceptedWithoutWake, context);
        }
        return null;
    }

    private BrainResponse handleCommonAssistantTools(String input, String lower, boolean acceptedWithoutWake, String context) {
        if (isWeatherLookupRequest(lower) && !isExplicitWebSearch(lower)) {
            String location = extractWeatherLocation(input);
            if (!location.isBlank()) lastWeatherLocation = location;
            if (location.isBlank()) location = lastWeatherLocation;
            String when = lower.contains("friday") ? "Friday" : lower.contains("tomorrow") ? "tomorrow" : lower.contains("tonight") ? "tonight" : "now";
            return action("Check weather", "weather_lookup", Map.of("location", location, "when", when), false, acceptedWithoutWake, context);
        }
        if (lower.startsWith("what about ") && !lastWeatherLocation.isBlank() && (lower.contains("friday") || lower.contains("tomorrow") || lower.contains("tonight"))) {
            String when = lower.contains("friday") ? "Friday" : lower.contains("tomorrow") ? "tomorrow" : "tonight";
            return action("Check follow-up weather", "weather_lookup", Map.of("location", lastWeatherLocation, "when", when), false, acceptedWithoutWake, context);
        }
        Matcher timer = Pattern.compile("(?i)set (?:a )?timer for (\\d+)\\s*(seconds?|minutes?|hours?)").matcher(input);
        if (startsAsRequest(lower, "set ", "start ") && timer.find()
                && !looksLikeDescriptiveContinuation(lower.substring(timer.end()).trim())) {
            return action("Set timer", "set_timer", Map.of("amount", timer.group(1), "unit", timer.group(2)), false, acceptedWithoutWake, context);
        }
        if (isDirectNavigationRequest(lower)) return action("Navigate", "navigate", Map.of("destination", input.replaceFirst("(?i)^(navigate|directions)(?:\\s+to)?\\s+", "")), false, acceptedWithoutWake, context);
        if (isDirectMediaPlayRequest(lower)) return action("Play media", "media_play", Map.of("query", input.substring(5).trim()), false, acceptedWithoutWake, context);
        if (isFlashlightCommand(lower)) return action("Set flashlight", "set_flashlight", Map.of("state", flashlightState(lower)), false, acceptedWithoutWake, context);
        if (isCalendarQueryRequest(lower)) return action("Query calendar", "calendar_query", Map.of("when", lower.contains("tomorrow") ? "tomorrow" : "today"), false, acceptedWithoutWake, context);
        if (isNotificationQueryRequest(lower)) return action("Query notifications", "notification_query", Map.of(), false, acceptedWithoutWake, context);
        if (isDirectTranslateRequest(lower)) return action("Translate", "translate", Map.of("request", input.substring(10).trim()), false, acceptedWithoutWake, context);
        Matcher text = Pattern.compile("(?i)^(?:text|message)\\s+([^,]+?)\\s+(.+)$").matcher(input);
        if (text.matches() && isHighConfidenceMessageRecipient(text.group(1))
                && !looksLikeDescriptiveContinuation(text.group(2).trim().toLowerCase(Locale.ROOT))
                && !looksLikeConditionalActionClause(text.group(2).trim().toLowerCase(Locale.ROOT))) {
            return action("Send message", "send_message", Map.of("recipient", text.group(1).trim(), "message", text.group(2).trim()), true, acceptedWithoutWake, context);
        }
        return null;
    }

    private static boolean looksLikeDescriptiveContinuation(String suffix) {
        return suffix.matches("(?:is|are|was|were|can|could|should|would|means|mean|refers|refer)\\b.*");
    }

    private static boolean looksLikeConditionalActionClause(String suffix) {
        return suffix.matches("(?:if|unless|when|whenever|after|before|once)\\b.*");
    }

    private static boolean isHighConfidenceMessageRecipient(String recipient) {
        String raw = recipient == null ? "" : recipient.trim();
        if (raw.isEmpty()) return false;
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.matches("messaging|message|messages|communication|communications")) return false;
        if (value.matches("mom|mum|mother|dad|father|brother|sister|wife|husband|partner|grandma|grandmother|grandpa|grandfather")) return true;
        char first = raw.charAt(0);
        return Character.isUpperCase(first) || Character.isDigit(first) || first == '+';
    }

    private static boolean isHighConfidenceCallRecipient(String recipient) {
        String raw = recipient == null ? "" : recipient.trim();
        if (raw.isEmpty()) return false;
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.startsWith("me ") || value.startsWith("it ")) return false;
        if (value.matches("mom|mum|mother|dad|father|brother|sister|wife|husband|partner|grandma|grandmother|grandpa|grandfather")) return true;
        char first = raw.charAt(0);
        if (!(Character.isUpperCase(first) || Character.isDigit(first) || first == '+')) return false;
        String[] words = value.split("\\s+");
        for (int i = 1; i < words.length; i++) {
            if (looksLikeDescriptiveContinuation(String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)))) return false;
        }
        return true;
    }

    private static boolean isReminderCreationRequest(String lower) {
        String value = lower.trim();
        if (!value.startsWith("remind me ")) return false;
        String request = value.substring("remind me ".length()).trim();
        return !request.matches("(?:why|how|what|who|where|when|which)\\b.*");
    }

    private static boolean isDirectNavigationRequest(String lower) {
        String value = lower.trim();
        if (value.startsWith("navigate ")) {
            return !value.matches("navigate(?:\\s+to)?\\s+.+\\s+(?:is|are|were|was|can|could|should|would|mean|means|refer|refers)\\b.*");
        }
        if (!value.startsWith("directions ")) return false;
        if (value.matches("directions\\s+(?:are|is|were|was|can|could|should|would|mean|means|refer|refers)\\b.*")) return false;
        return !value.matches("directions(?:\\s+to)?\\s+.+\\s+(?:is|are|were|was|can|could|should|would|mean|means|refer|refers)\\b.*");
    }

    private static boolean isDirectMediaPlayRequest(String lower) {
        if (!lower.startsWith("play ")) return false;
        if (lower.matches("play\\s+(?:is|was|were|has|had|can|could|should|would|means|refers)\\b.*")) return false;
        if (lower.matches("play\\s+.+\\s+(?:can|could|should|would)\\s+(?:be|help|improve|increase|reduce|make|keep|cause|mean|refer)\\b.*")) return false;
        return !lower.matches("play\\s+(?:music|media|track|song)\\s+(?:is|are|was|were|has|had|can|could|should|would|means|refers)\\b.*");
    }

    private static boolean isDirectTranslateRequest(String lower) {
        if (!lower.startsWith("translate ")) return false;
        return !lower.matches("translate\\s+(?:is|was|were|has|had|can|could|should|would|means|refers)\\b.*");
    }

    private static boolean isExplicitWebSearch(String lower) {
        return lower.contains("search the web for ") || lower.contains("search web for ")
                || lower.contains("search online for ") || lower.contains("look up online ");
    }

    private static boolean isWeatherLookupRequest(String lower) {
        if (!lower.contains("weather")) return false;
        String value = lower.trim();
        return value.startsWith("weather ") || value.equals("weather")
                || value.startsWith("what's the weather") || value.startsWith("what is the weather")
                || value.startsWith("how's the weather") || value.startsWith("how is the weather")
                || value.startsWith("check the weather") || value.startsWith("check weather")
                || value.startsWith("show me the weather") || value.startsWith("tell me the weather");
    }

    private static boolean isCalendarQueryRequest(String lower) {
        if (!lower.contains("calendar")) return false;
        String value = lower.trim();
        if (value.matches("calendar\\s+(?:today|tomorrow)\\s+(?:is|are|was|were|can|could|should|would|means|refers)\\b.*")) return false;
        if (value.matches("(?:show|check|read)\\s+(?:(?:me\\s+)?(?:my\\s+|the\\s+)?)?calendar(?:\\s+(?:today|tomorrow))?\\s+(?:is|are|was|were|can|could|should|would|means|refers)\\b.*")) return false;
        if (value.matches("(?:what(?:'s| is)\\s+on\\s+(?:my|the)\\s+calendar|what\\s+do\\s+i\\s+have\\s+on\\s+my\\s+calendar)(?:\\s+(?:today|tomorrow))?\\s+(?:is|are|was|were|can|could|should|would|means|refers)\\b.*")) return false;
        return value.startsWith("what's on my calendar") || value.startsWith("what is on my calendar")
                || value.startsWith("what do i have on my calendar") || value.startsWith("what's on the calendar")
                || value.startsWith("what is on the calendar") || value.startsWith("show my calendar")
                || value.startsWith("show me my calendar") || value.startsWith("check my calendar")
                || value.startsWith("check the calendar") || value.startsWith("read my calendar")
                || value.startsWith("calendar today") || value.startsWith("calendar tomorrow");
    }

    private static boolean isNotificationQueryRequest(String lower) {
        if (!lower.contains("notification")) return false;
        String value = lower.trim();
        if (value.matches("(?:read|show|check)\\s+(?:my\\s+)?notifications?\\s+(?:is|are|was|were|can|could|should|would|means|refers)\\b.*")) return false;
        if (value.matches("what notifications?\\s+(?:is|are|was|were|can|could|should|would)\\s+(?!i\\b|we\\b|you\\b|on\\b|from\\b|there\\b|waiting\\b|unread\\b).+")) return false;
        return value.startsWith("what notification") || value.startsWith("what notifications")
                || value.startsWith("what are my notification") || value.startsWith("what are my notifications")
                || value.startsWith("show notification") || value.startsWith("show notifications")
                || value.startsWith("show me my notification") || value.startsWith("show me my notifications")
                || value.startsWith("check notification") || value.startsWith("check notifications")
                || value.startsWith("read notification") || value.startsWith("read notifications")
                || value.startsWith("do i have any notification") || value.startsWith("any notification");
    }

    private static boolean startsAsRequest(String lower, String... commandPrefixes) {
        String value = lower.trim();
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

    private static boolean isFlashlightCommand(String lower) {
        String value = lower.trim();
        String[] polite = {"please ", "can you ", "could you ", "would you ", "will you ", "jarvis ", "i want you to ", "i need you to "};
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
        value = value.replaceFirst("[,!.?]*\\s+please[.!?]*$", "").trim();
        return value.matches("(?:(?:turn|switch)\\s+(?:(?:on|off)\\s+(?:the\\s+)?flashlight|(?:the\\s+)?flashlight\\s+(?:on|off))|(?:enable|disable|kill)\\s+(?:the\\s+)?flashlight|flashlight\\s+(?:on|off))");
    }

    private static String flashlightState(String lower) {
        return lower.contains("off") || lower.contains("disable") || lower.contains("kill") ? "off" : "on";
    }

    private static String extractWeatherLocation(String input) {
        Matcher m = Pattern.compile("(?i)weather(?: like)?(?: in| for)\\s+(.+?)(?:\\s+(?:today|tonight|tomorrow|friday|this weekend))?[?]?$", Pattern.CASE_INSENSITIVE).matcher(input);
        return m.find() ? m.group(1).trim().replaceAll("[?]$", "") : "";
    }

    private BrainResponse action(String goal, String tool, Map<String, String> args, boolean consequential, boolean acceptedWithoutWake, String context) {
        return BrainResponse.of(BrainResponse.Kind.ACTION_PLAN, "Understood.", new Plan(goal, List.of(new PlanStep(tool, args, consequential))), true, acceptedWithoutWake, context);
    }

    private static boolean isHelpRequest(String lower) {
        String normalized = lower.replaceAll("[^a-z0-9']+", " ").trim();
        return normalized.matches("help(?: me)?|what can you do|what do you do|capabilities|show me what you can do");
    }
    private static boolean isDialerAlias(String lower) { return lower.matches("(?:open\\s+)?(?:the\\s+)?(?:phone(?:\\s+app)?|dialer|calls?|telephone)"); }
    private static boolean isDinnerDiscovery(String lower, String context) { if (lower.contains("find me a place to eat") || lower.contains("find me somewhere good") || lower.contains("where should i eat") || lower.contains("dinner tonight")) return true; return lower.contains("find me somewhere") && (context.contains("food") || context.contains("italian") || context.contains("dinner")); }
    private static String normalizeTime(String hourRaw, String minuteRaw, String meridiemRaw) { int hour = Integer.parseInt(hourRaw); int minute = minuteRaw == null ? 0 : Integer.parseInt(minuteRaw); String meridiem = meridiemRaw == null ? (hour <= 7 ? "PM" : "AM") : meridiemRaw.toUpperCase(Locale.ROOT); return String.format(Locale.ROOT, "%d:%02d %s", hour, minute, meridiem); }
}
