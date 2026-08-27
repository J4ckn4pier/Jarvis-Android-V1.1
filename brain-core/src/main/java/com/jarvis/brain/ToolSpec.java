package com.jarvis.brain;

import java.util.Set;

public record ToolSpec(String name, boolean consequential, Set<String> aliases,
                       Set<String> requiredArguments, String description) {
    public ToolSpec {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
        requiredArguments = requiredArguments == null ? Set.of() : Set.copyOf(requiredArguments);
        description = description == null ? "" : description;
    }
    public ToolSpec(String name, boolean consequential, Set<String> aliases) { this(name, consequential, aliases, Set.of(), ""); }
}
