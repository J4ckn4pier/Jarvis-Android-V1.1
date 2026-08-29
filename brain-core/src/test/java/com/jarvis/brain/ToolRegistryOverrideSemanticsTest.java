package com.jarvis.brain;

import java.util.Set;

/** Re-registering a canonical tool must replace its still-owned aliases without stealing aliases now owned elsewhere. */
public final class ToolRegistryOverrideSemanticsTest {
    public static void main(String[] args) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        ToolSpec original = new ToolSpec(
                "open_dialer",
                false,
                Set.of("phone", "phone app", "dialer", "calls", "call", "telephone"),
                Set.of(),
                "Original dialer placeholder",
                ToolExecutionClass.DEVICE_REFLEX);
        registry.register(original, (arguments, context) -> ToolResult.success("old-placeholder"));

        ToolSpec collision = new ToolSpec(
                "call_history",
                false,
                Set.of("calls"),
                Set.of(),
                "Separate call-history capability",
                ToolExecutionClass.DEVICE_REFLEX);
        registry.register(collision, (arguments, context) -> ToolResult.success("history"));

        ToolSpec androidOverride = new ToolSpec(
                "open_dialer",
                false,
                Set.of("phone", "phone app", "dialer"),
                Set.of(),
                "Android dialer implementation",
                ToolExecutionClass.DEVICE_REFLEX);
        registry.register(androidOverride, (arguments, context) -> ToolResult.success("android-dialer"));

        check("android-dialer".equals(run(registry, "open_dialer")), "canonical name must resolve to replacement implementation");
        check("android-dialer".equals(run(registry, "phone")), "new alias must resolve to replacement implementation");
        check("android-dialer".equals(run(registry, "call")), "still-owned legacy alias must migrate to replacement implementation");
        check("android-dialer".equals(run(registry, "telephone")), "all still-owned legacy aliases must migrate to replacement implementation");
        check("history".equals(run(registry, "calls")), "replacement must not steal a legacy alias already reassigned to another canonical tool");

        ToolSpec effectiveDialer = registry.specs().stream()
                .filter(spec -> "open_dialer".equals(spec.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("replacement canonical spec missing"));
        check(effectiveDialer.aliases().contains("call"), "effective replacement spec must retain migrated legacy alias metadata");
        check(effectiveDialer.aliases().contains("telephone"), "effective replacement spec must expose all still-owned legacy aliases");
        check(!effectiveDialer.aliases().contains("calls"), "effective replacement spec must not advertise an alias now owned by another tool");

        System.out.println("ToolRegistryOverrideSemanticsTest passed");
    }

    private static String run(ToolRegistry registry, String name) throws Exception {
        ToolRegistry.RegisteredTool tool = registry.resolve(name)
                .orElseThrow(() -> new AssertionError("missing tool alias: " + name));
        ToolResult result = tool.implementation().execute(java.util.Map.of(), new ToolContext("test", "test"));
        return result.output();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
