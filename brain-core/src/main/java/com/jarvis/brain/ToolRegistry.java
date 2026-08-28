package com.jarvis.brain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ToolRegistry {
    public record RegisteredTool(ToolSpec spec, Tool implementation) { public String name() { return spec.name(); } }
    private final Map<String, RegisteredTool> byKey = new HashMap<>();

    public void register(ToolSpec spec, Tool implementation) {
        RegisteredTool registered = new RegisteredTool(spec, implementation);
        byKey.put(normalize(spec.name()), registered);
        for (String alias : spec.aliases()) byKey.put(normalize(alias), registered);
    }

    public Optional<RegisteredTool> resolve(String nameOrAlias) {
        return Optional.ofNullable(byKey.get(normalize(nameOrAlias)));
    }

    /**
     * Tokens that are likely incomplete when they are the final spoken token because
     * the resolved tool still requires arguments. This keeps endpointing and planning
     * on the same source of truth instead of maintaining a separate verb list.
     */
    public Set<String> incompleteTrailingTokens() {
        Set<String> out = new HashSet<>();
        Set<RegisteredTool> unique = new HashSet<>(byKey.values());
        for (RegisteredTool registered : unique) {
            ToolSpec spec = registered.spec();
            if (spec.requiredArguments().isEmpty()) continue;
            addFirstToken(out, spec.name().replace('_', ' '));
            for (String alias : spec.aliases()) addFirstToken(out, alias);
        }
        return Set.copyOf(out);
    }

    public static ToolRegistry standard() {
        ToolRegistry r = new ToolRegistry();
        r.register(spec("open_dialer", false, Set.of("phone", "phone app", "dialer", "calls", "call", "telephone"), Set.of(), "Open the phone dialer"), ready("dialer-ready"));
        r.register(spec("discover_places", false, Set.of("restaurants", "find food", "dinner"), Set.of("category"), "Discover nearby places"), ready("place-discovery-ready"));
        r.register(spec("rank_options", false, Set.of(), Set.of(), "Rank candidate options using user context"), ready("ranking-ready"));
        r.register(spec("present_options", false, Set.of(), Set.of(), "Present ranked options"), ready("presentation-ready"));
        r.register(spec("resolve_business", false, Set.of(), Set.of("business"), "Resolve a named business/entity"), ready("business-resolution-ready"));
        r.register(spec("place_conversational_call", true, Set.of("call business", "phone agent"), Set.of("business", "goal"), "Conduct an approved outbound conversational call"), (a,c) -> ToolResult.failure("telephony adapter not attached"));
        r.register(spec("report_outcome", false, Set.of(), Set.of(), "Report a completed multi-step action"), ready("report-ready"));
        r.register(spec("weather_lookup", false, Set.of("weather", "forecast"), Set.of("when"), "Look up weather/forecast"), ready("weather-ready"));
        r.register(spec("set_timer", false, Set.of("timer"), Set.of("amount", "unit"), "Set a device timer"), ready("timer-ready"));
        r.register(spec("create_reminder", false, Set.of("reminder", "remind me"), Set.of("request"), "Create a personal reminder"), ready("reminder-ready"));
        r.register(spec("navigate", false, Set.of("directions", "navigation"), Set.of("destination"), "Navigate to a destination"), ready("navigation-ready"));
        r.register(spec("media_play", false, Set.of("play music", "play media"), Set.of("query"), "Play requested media"), ready("media-ready"));
        r.register(spec("set_flashlight", false, Set.of("flashlight", "torch"), Set.of("state"), "Turn flashlight on/off"), ready("flashlight-ready"));
        r.register(spec("calendar_query", false, Set.of("calendar", "schedule"), Set.of("when"), "Read calendar commitments"), ready("calendar-ready"));
        r.register(spec("notification_query", false, Set.of("notifications"), Set.of(), "Read captured notifications"), ready("notifications-ready"));
        r.register(spec("translate", false, Set.of("translation"), Set.of("request"), "Translate text"), ready("translation-ready"));
        r.register(spec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"), "Send an external message on the user's behalf"), ready("message-ready"));
        return r;
    }

    private static ToolSpec spec(String name, boolean consequential, Set<String> aliases, Set<String> required, String description) {
        return new ToolSpec(name, consequential, aliases, required, description);
    }

    private static Tool ready(String value) { return (args, ctx) -> ToolResult.success(value); }

    private static void addFirstToken(Set<String> out, String phrase) {
        String normalized = normalize(phrase);
        if (normalized.isEmpty()) return;
        int space = normalized.indexOf(' ');
        out.add(space < 0 ? normalized : normalized.substring(0, space));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
