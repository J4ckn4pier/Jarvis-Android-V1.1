package com.jarvis.brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ToolRegistry {
    public record RegisteredTool(ToolSpec spec, Tool implementation) { public String name() { return spec.name(); } }
    private final Map<String, RegisteredTool> byKey = new HashMap<>();
    public void register(ToolSpec spec, Tool implementation) {
        String canonical = normalize(spec.name()); RegisteredTool previous = byKey.get(canonical); Set<String> aliases = new HashSet<>();
        if (previous != null && normalize(previous.spec().name()).equals(canonical)) {
            for (String requestedAlias : spec.aliases()) { String key = normalize(requestedAlias); RegisteredTool owner = byKey.get(key); if (owner == null || owner == previous || normalize(owner.spec().name()).equals(canonical)) aliases.add(key); }
            for (Map.Entry<String, RegisteredTool> entry : byKey.entrySet()) if (entry.getValue() == previous && !entry.getKey().equals(canonical)) aliases.add(entry.getKey());
            byKey.entrySet().removeIf(entry -> entry.getValue() == previous);
        } else aliases.addAll(spec.aliases());
        ToolSpec effectiveSpec = new ToolSpec(spec.name(), spec.consequential(), aliases, spec.requiredArguments(), spec.description(), spec.executionClass()); RegisteredTool registered = new RegisteredTool(effectiveSpec, implementation); byKey.put(canonical, registered); for (String alias : aliases) byKey.put(normalize(alias), registered);
    }
    public Optional<RegisteredTool> resolve(String nameOrAlias) { return Optional.ofNullable(byKey.get(normalize(nameOrAlias))); }
    public List<ToolSpec> specs() { Set<RegisteredTool> unique = new HashSet<>(byKey.values()); ArrayList<ToolSpec> specs = new ArrayList<>(); for (RegisteredTool registered : unique) specs.add(registered.spec()); specs.sort(Comparator.comparing(ToolSpec::name)); return List.copyOf(specs); }
    public Set<String> incompleteTrailingTokens() { Set<String> out = new HashSet<>(); Set<RegisteredTool> unique = new HashSet<>(byKey.values()); for (RegisteredTool registered : unique) { ToolSpec spec = registered.spec(); if (spec.requiredArguments().isEmpty()) continue; addFirstToken(out, spec.name().replace('_', ' ')); for (String alias : spec.aliases()) addFirstToken(out, alias); } return Set.copyOf(out); }
    public static ToolRegistry standard() { return standard(ExternalResearchGateway.unavailable(), null); }
    public static ToolRegistry standard(ExternalResearchGateway research) { return standard(research, null); }
    public static ToolRegistry standard(ExternalResearchGateway research, ConversationalCallTransport callTransport) {
        ExternalResearchGateway gateway = research == null ? ExternalResearchGateway.unavailable() : research; ToolRegistry r = new ToolRegistry();
        r.register(spec("open_dialer", false, Set.of("phone", "phone app", "dialer", "calls", "call", "telephone"), Set.of(), "Open the phone dialer", ToolExecutionClass.DEVICE_REFLEX), ready("dialer-ready"));
        r.register(spec("call_contact", true, Set.of("call contact", "phone contact"), Set.of("recipient"), "Place an approved phone call to one exact contact or explicit phone number", ToolExecutionClass.CONSEQUENTIAL), ready("contact-call-ready"));
        r.register(spec("open_app", false, Set.of("launch app", "open application"), Set.of("app"), "Open an installed app by exact visible app name", ToolExecutionClass.DEVICE_REFLEX), ready("app-ready"));
        r.register(spec("web_search", false, Set.of("search web", "web search", "search online", "look up online"), Set.of("query"), "Search the web for a user-provided query", ToolExecutionClass.DEVICE_REFLEX), ready("web-search-ready"));
        r.register(spec("discover_places", false, Set.of("restaurants", "find food", "dinner"), Set.of("category"), "Discover nearby places; cuisine/type is arbitrary user data, not a fixed enum", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::discoverPlaces);
        r.register(spec("rank_options", false, Set.of(), Set.of(), "Rank candidate options using user context and evidence", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::rankOptions);
        r.register(spec("present_options", false, Set.of(), Set.of(), "Present ranked options from evidence", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::presentOptions);
        r.register(spec("resolve_business", false, Set.of(), Set.of("business"), "Resolve a named business/entity", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::resolveBusiness);
        r.register(spec("get_menu", false, Set.of("menu", "menu prices", "dish prices"), Set.of("business"), "Read fresh menu items and prices with source provenance", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::getMenu);
        r.register(spec("attempt_reservation", true, Set.of("book table", "reserve table", "online reservation"), Set.of("business", "party_size", "requested_time"), "Attempt an approved online reservation; return confirmed time, actual available alternatives, or failure reason", ToolExecutionClass.CONSEQUENTIAL), gateway::attemptReservation);
        Tool conversationalCall = callTransport == null ? (a,c) -> ToolResult.failure("telephony adapter not attached") : (a,c) -> executeConversationalCall(callTransport, a);
        r.register(spec("place_conversational_call", true, Set.of("call business", "phone agent"), Set.of("business", "destination", "represented_user", "preferred_time"), "Conduct an approved outbound conversational call using a resolved destination and explicit represented-user/time context", ToolExecutionClass.CONSEQUENTIAL), conversationalCall);
        r.register(spec("report_outcome", false, Set.of(), Set.of(), "Report an evidence-backed completed multi-step action", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::reportOutcome);
        r.register(spec("weather_lookup", false, Set.of("weather", "forecast"), Set.of("when"), "Look up weather/forecast", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::weatherLookup);
        r.register(spec("set_timer", false, Set.of("timer"), Set.of("amount", "unit"), "Set a device timer", ToolExecutionClass.DEVICE_REFLEX), ready("timer-ready"));
        r.register(spec("set_alarm", false, Set.of("alarm", "set alarm"), Set.of("hour", "minute"), "Set a local device alarm using 24-hour clock values", ToolExecutionClass.DEVICE_REFLEX), ready("alarm-ready"));
        r.register(spec("create_reminder", false, Set.of("reminder", "remind me"), Set.of("title", "start_millis"), "Open a timed personal reminder draft for user confirmation using a title and resolved epoch-millis start time", ToolExecutionClass.DEVICE_REFLEX), ready("reminder-ready"));
        r.register(spec("compose_calendar_event", false, Set.of("create calendar event", "add calendar event", "schedule event", "invite attendees"), Set.of("title", "start_millis", "end_millis"), "Open a structured calendar event draft for user confirmation, optionally including location and attendee emails", ToolExecutionClass.DEVICE_REFLEX), ready("calendar-event-ready"));
        r.register(spec("navigate", false, Set.of("directions", "navigation"), Set.of("destination"), "Navigate to a destination", ToolExecutionClass.DEVICE_REFLEX), ready("navigation-ready"));
        r.register(spec("device_navigation", false, Set.of("back", "go back", "home", "go home", "scroll down", "scroll up"), Set.of("action"), "Safely navigate the current Android interface using back, home, scroll down, or scroll up", ToolExecutionClass.DEVICE_REFLEX), ready("device-navigation-ready"));
        r.register(spec("screen_read", false, Set.of("read screen", "what's on my screen", "what is on my screen"), Set.of(), "Read visible text from the current Android screen through the enabled accessibility service", ToolExecutionClass.DEVICE_REFLEX), ready("screen-read-ready"));
        r.register(spec("ui_click", true, Set.of("click visible control", "tap visible control"), Set.of("target"), "After approval, click exactly one uniquely matching visible Android control", ToolExecutionClass.CONSEQUENTIAL), ready("ui-click-ready"));
        r.register(spec("ui_type", true, Set.of("type into field", "enter into field"), Set.of("text"), "After approval, type into the one uniquely selected editable Android field", ToolExecutionClass.CONSEQUENTIAL), ready("ui-type-ready"));
        r.register(spec("media_play", false, Set.of("play music", "play media"), Set.of("query"), "Play requested media", ToolExecutionClass.DEVICE_REFLEX), ready("media-ready"));
        r.register(spec("media_control", false, Set.of("pause media", "resume media", "next track", "previous track"), Set.of("action"), "Control current media playback with pause/play/next/previous actions", ToolExecutionClass.DEVICE_REFLEX), ready("media-control-ready"));
        r.register(spec("volume_control", false, Set.of("volume", "volume up", "volume down", "mute", "unmute"), Set.of("action"), "Control device volume with up/down/mute/unmute actions", ToolExecutionClass.DEVICE_REFLEX), ready("volume-control-ready"));
        r.register(spec("set_flashlight", false, Set.of("flashlight", "torch"), Set.of("state"), "Turn flashlight on/off", ToolExecutionClass.DEVICE_REFLEX), ready("flashlight-ready"));
        r.register(spec("calendar_query", false, Set.of("calendar", "schedule"), Set.of("when"), "Read calendar commitments", ToolExecutionClass.DEVICE_REFLEX), ready("calendar-ready"));
        r.register(spec("notification_query", false, Set.of("notifications"), Set.of(), "Read captured notifications", ToolExecutionClass.DEVICE_REFLEX), ready("notifications-ready"));
        r.register(spec("translate", false, Set.of("translation"), Set.of("request"), "Translate text through the provider-neutral language gateway", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::translate);
        r.register(spec("compose_email", false, Set.of("email", "compose email", "draft email"), Set.of("recipient", "subject", "body"), "Open an email draft for user review without sending it", ToolExecutionClass.DEVICE_REFLEX), (args, ctx) -> ToolResult.failure("email adapter not attached"));
        r.register(spec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"), "Send an external message on the user's behalf", ToolExecutionClass.CONSEQUENTIAL), ready("message-ready")); return r;
    }
    private static ToolResult executeConversationalCall(ConversationalCallTransport transport, Map<String,String> args) { ConversationalCallRequest request = new ConversationalCallRequest(args.get("destination"), args.get("business"), args.get("represented_user"), args.get("preferred_time")); CallOutcome outcome = new ConversationalCallOrchestrator(8).execute(transport, request); return switch (outcome.status()) { case CONFIRMED -> ToolResult.success("status=CONFIRMED|confirmed_time=" + outcome.confirmedTime() + "|summary=" + outcome.summary()); case ALTERNATIVES_AVAILABLE -> ToolResult.success("status=ALTERNATIVES_AVAILABLE|alternatives=" + String.join(",", outcome.alternatives()) + "|summary=" + outcome.summary()); case FAILED -> ToolResult.failure("status=FAILED|summary=" + outcome.summary()); case IN_PROGRESS -> ToolResult.failure("status=FAILED|summary=Conversational call ended without a terminal outcome."); }; }
    private static ToolSpec spec(String name, boolean consequential, Set<String> aliases, Set<String> required, String description, ToolExecutionClass executionClass) { return new ToolSpec(name, consequential, aliases, required, description, executionClass); }
    private static Tool ready(String value) { return (args, ctx) -> ToolResult.success(value); }
    private static void addFirstToken(Set<String> out, String phrase) { String normalized = normalize(phrase); if (normalized.isEmpty()) return; int space = normalized.indexOf(' '); out.add(space < 0 ? normalized : normalized.substring(0, space)); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
}
