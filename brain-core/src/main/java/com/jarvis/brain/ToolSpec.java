package com.jarvis.brain;

import java.util.Set;

public record ToolSpec(String name, boolean consequential, Set<String> aliases,
                       Set<String> requiredArguments, String description, ToolExecutionClass executionClass) {
    public ToolSpec {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
        requiredArguments = requiredArguments == null ? Set.of() : Set.copyOf(requiredArguments);
        description = description == null ? "" : description;
        executionClass = executionClass == null
                ? (consequential ? ToolExecutionClass.CONSEQUENTIAL : ToolExecutionClass.DEVICE_REFLEX)
                : executionClass;
        if (consequential && executionClass != ToolExecutionClass.CONSEQUENTIAL) {
            throw new IllegalArgumentException("consequential tools must use CONSEQUENTIAL execution class");
        }
        if (!consequential && executionClass == ToolExecutionClass.CONSEQUENTIAL) {
            throw new IllegalArgumentException("CONSEQUENTIAL execution class requires consequential=true");
        }
    }
    public ToolSpec(String name, boolean consequential, Set<String> aliases, Set<String> requiredArguments, String description) {
        this(name, consequential, aliases, requiredArguments, description,
                consequential ? ToolExecutionClass.CONSEQUENTIAL : ToolExecutionClass.DEVICE_REFLEX);
    }
    public ToolSpec(String name, boolean consequential, Set<String> aliases) {
        this(name, consequential, aliases, Set.of(), "");
    }
}
