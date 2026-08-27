package com.jarvis.brain;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {
    public record RegisteredTool(ToolSpec spec, Tool implementation) {
        public String name() { return spec.name(); }
    }

    private final Map<String, RegisteredTool> byKey = new HashMap<>();

    public void register(ToolSpec spec, Tool implementation) {
        RegisteredTool registered = new RegisteredTool(spec, implementation);
        byKey.put(normalize(spec.name()), registered);
        for (String alias : spec.aliases()) byKey.put(normalize(alias), registered);
    }

    public Optional<RegisteredTool> resolve(String nameOrAlias) {
        return Optional.ofNullable(byKey.get(normalize(nameOrAlias)));
    }

    public static ToolRegistry standard() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("open_dialer", false,
                        java.util.Set.of("phone", "phone app", "dialer", "calls", "call", "telephone")),
                (args, ctx) -> ToolResult.success("dialer-ready"));
        registry.register(new ToolSpec("discover_places", false,
                        java.util.Set.of("restaurants", "find food", "dinner")),
                (args, ctx) -> ToolResult.success("place-discovery-ready"));
        registry.register(new ToolSpec("rank_options", false, java.util.Set.of()),
                (args, ctx) -> ToolResult.success("ranking-ready"));
        registry.register(new ToolSpec("resolve_business", false, java.util.Set.of()),
                (args, ctx) -> ToolResult.success("business-resolution-ready"));
        registry.register(new ToolSpec("place_conversational_call", true,
                        java.util.Set.of("call business", "phone agent")),
                (args, ctx) -> ToolResult.failure("telephony adapter not attached"));
        registry.register(new ToolSpec("report_outcome", false, java.util.Set.of()),
                (args, ctx) -> ToolResult.success("report-ready"));
        return registry;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
