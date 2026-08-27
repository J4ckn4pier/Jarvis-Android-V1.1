package com.jarvis.brain;

import java.util.Set;

public record ToolSpec(String name, boolean consequential, Set<String> aliases) {
    public ToolSpec {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
    }
}
