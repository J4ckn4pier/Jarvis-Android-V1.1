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
        String canonical = normalize(spec.name());
        RegisteredTool previous = byKey.get(canonical);
        Set<String> aliases = new HashSet<>();

        if (previous != null && normalize(previous.spec().name()).equals(canonical)) {
            for (String requestedAlias : spec.aliases()) {
                String key = normalize(requestedAlias);
                RegisteredTool owner = byKey.get(key);
                if (owner == null || owner == previous || normalize(owner.spec().name()).equals(canonical)) aliases.add(key);
            }
            for (Map.Entry<String, RegisteredTool> entry : byKey.entrySet()) {
                if (entry.getValue() == previous && !entry.getKey().equals(canonical)) aliases.add(entry.getKey());
            }
            byKey.entrySet().removeIf(entry -> entry.getValue() == previous);
        } else {
            aliases.addAll(spec.aliases());
        }

        ToolSpec effectiveSpec = new ToolSpec(
                spec.name(),
                spec.consequential(),
                aliases,
                spec.requiredArguments(),
                spec.description(),
                spec.executionClass());
        RegisteredTool registered = new RegisteredTool(effectiveSpec, implementation);
        byKey.put(canonical, registered);
        for (String alias : aliases) byKey.put(normalize(alias), registered);
    }

    public Optional<RegisteredTool> resolve(String nameOrAlias) { return Optional.ofNullable(byKey.get(normalize(nameOrAlias))); }

    public List<ToolSpec> specs() {
        Set<RegisteredTool> unique = new HashSet<>(byKey.values()); ArrayList<ToolSpec> specs = new ArrayList<>();
        for (RegisteredTool registered : unique) specs.add(registered.spec());
        specs.sort(Comparator.comparing(ToolSpec::name)); return List.copyOf(specs);
    }

    public Set<String> incompleteTrailingTokens() {
        Set<String> out = new HashSet<>(); Set<RegisteredTool> unique = new HashSet<>(byKey.values());
        for (RegisteredTool registered : unique) {
            ToolSpec spec = registered.spec(); if (spec.requiredArguments().isEmpty()) continue;
            addFirstToken(out, spec.name().replace('_', ' ')); for (String alias : spec.aliases()) addFirstToken(out, alias);
        }
        return Set.copyOf(out);
    }

    public static ToolRegistry standard() {
        return standard(ExternalResearchGateway.unavailable(), null);
    }

    public static ToolRegistry standard(ExternalResearchGateway research) {
        return standard(research, null);
    }

    public static ToolRegistry standard(ExternalResearchGateway research, ConversationalCallTransport callTransport) {
        ExternalResearchGateway gateway = research == null ? ExternalResearchGateway.unavailable() : research;
        ToolRegistry r = new ToolRegistry();
        r.register(spec("open_dialer", false, Set.of("phone", "phone app", "dialer", "calls", "call", "telephone"), Set.of(), "Open the phone dialer", ToolExecutionClass.DEVICE_REFLEX), ready("dialer-ready"));
        r.register(spec("discover_places", false, Set.of("restaurants", "find food", "dinner"), Set.of("category"), "Discover nearby places; cuisine/type is arbitrary user data, not a fixed enum", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::discoverPlaces);
        r.register(spec("rank_options", false, Set.of(), Set.of(), "Rank candidate options using user context and evidence", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::rankOptions);
        r.register(spec("present_options", false, Set.of(), Set.of(), "Present ranked options from evidence", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::presentOptions);
        r.register(spec("resolve_business", false, Set.of(), Set.of("business"), "Resolve a named business/entity", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::resolveBusiness);
        r.register(spec("get_menu", false, Set.of("menu", "menu prices", "dish prices"), Set.of("business"), "Read fresh menu items and prices with source provenance", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::getMenu);
        r.register(spec("attempt_reservation", true, Set.of("book table", "reserve table", "online reservation"), Set.of("business", "party_size", "requested_time"), "Attempt an approved online reservation; return confirmed time, actual available alternatives, or failure reason", ToolExecutionClass.CONSEQUENTIAL), gateway::attemptReservation);
        Tool conversationalCall = callTransport == null
                ? (a,c) -> ToolResult.failure("telephony adapter not attached")
                : (a,c) -> executeConversationalCall(callTransport, a);
        r.register(spec("place_conversational_call", true, Set.of("call business", "phone agent"), Set.of("business", "destination", "represented_user", "preferred_time"), "Conduct an approved outbound conversational call using a resolved destination and explicit represented-user/time context", ToolExecutionClass.CONSEQUENTIAL), conversationalCall);
        r.register(spec("report_outcome", false, Set.of(), Set.of(), "Report an evidence-backed completed multi-step action", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::reportOutcome);
        r.register(spec("weather_lookup", false, Set.of("weather", "forecast"), Set.of("when"), "Look up weather/forecast", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::weatherLookup);
        r.register(spec("set_timer", false, Set.of("timer"), Set.of("amount", "unit"), "Set a device timer", ToolExecutionClass.DEVICE_REFLEX), ready("timer-ready"));
        r.register(spec("create_reminder", false, Set.of("reminder", "remind me"), Set.of("request"), "Create a personal reminder", ToolExecutionClass.DEVICE_REFLEX), ready("reminder-ready"));
        r.register(spec("navigate", false, Set.of("directions", "navigation"), Set.of("destination"), "Navigate to a destination", ToolExecutionClass.DEVICE_REFLEX), ready("navigation-ready"));
        r.register(spec("media_play", false, Set.of("play music", "play media"), Set.of("query"), "Play requested media", ToolExecutionClass.DEVICE_REFLEX), ready("media-ready"));
        r.register(spec("set_flashlight", false, Set.of("flashlight", "torch"), Set.of("state"), "Turn flashlight on/off", ToolExecutionClass.DEVICE_REFLEX), ready("flashlight-ready"));
        r.register(spec("calendar_query", false, Set.of("calendar", "schedule"), Set.of("when"), "Read calendar commitments", ToolExecutionClass.DEVICE_REFLEX), ready("calendar-ready"));
        r.register(spec("notification_query", false, Set.of("notifications"), Set.of(), "Read captured notifications", ToolExecutionClass.DEVICE_REFLEX), ready("notifications-ready"));
        r.register(spec("translate", false, Set.of("translation"), Set.of("request"), "Translate text through the provider-neutral language gateway", ToolExecutionClass.AUTONOMOUS_RESEARCH), gateway::translate);
        r.register(spec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"), "Send an external message on the user's behalf", ToolExecutionClass.CONSEQUENTIAL), ready("message-ready"));
        return r;
    }

    private static ToolResult executeConversationalCall(ConversationalCallTransport transport, Map<String,String> args) {
        ConversationalCallRequest request = new ConversationalCallRequest(
                args.get("destination"),
                args.get("business"),
                args.get("represented_user"),
                args.get("preferred_time"));
        CallOutcome outcome = new ConversationalCallOrchestrator(8).execute(transport, request);
        return switch (outcome.status()) {
            case CONFIRMED -> ToolResult.success(
                    "status=CONFIRMED|confirmed_time=" + outcome.confirmedTime() + "|summary=" + outcome.summary());
            case ALTERNATIVES_AVAILABLE -> ToolResult.success(
                    "status=ALTERNATIVES_AVAILABLE|alternatives=" + String.join(",", outcome.alternatives()) + "|summary=" + outcome.summary());
            case FAILED -> ToolResult.failure("status=FAILED|summary=" + outcome.summary());
            case IN_PROGRESS -> ToolResult.failure("status=FAILED|summary=Conversational call ended without a terminal outcome.");
        };
    }

    private static ToolSpec spec(String name, boolean consequential, Set<String> aliases, Set<String> required, String description, ToolExecutionClass executionClass) {
        return new ToolSpec(name, consequential, aliases, required, description, executionClass);
    }
    private static Tool ready(String value) { return (args, ctx) -> ToolResult.success(value); }
    private static void addFirstToken(Set<String> out, String phrase) { String normalized = normalize(phrase); if (normalized.isEmpty()) return; int space = normalized.indexOf(' '); out.add(space < 0 ? normalized : normalized.substring(0, space)); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
}
