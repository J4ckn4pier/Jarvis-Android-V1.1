package com.jarvis.brain;

import java.util.Map;
import java.util.Set;

/** Pins provider-neutral email composition semantics before Android binds a real compose surface. */
public final class EmailCompositionToolContractTest {
    public static void main(String[] args) {
        ToolRegistry registry = ToolRegistry.standard();
        ToolRegistry.RegisteredTool email = registry.resolve("compose_email")
                .orElseThrow(() -> new AssertionError("standard registry must expose compose_email"));

        ToolSpec spec = email.spec();
        check(!spec.consequential(), "opening an email compose/review surface must not be classified as sending");
        check(spec.executionClass() == ToolExecutionClass.DEVICE_REFLEX,
                "email composition is a device action, not autonomous research or a sent-message side effect");
        check(spec.requiredArguments().equals(Set.of("recipient", "subject", "body")),
                "compose_email must keep recipient, subject, and body as structured required arguments");

        ToolResult unattached = email.implementation().execute(
                Map.of("recipient", "person@example.com", "subject", "Hello", "body", "Draft body"),
                new ExecutionContext());
        check(!unattached.success(), "provider-neutral registry must fail closed until a platform email adapter is attached");
        check(unattached.message().toLowerCase().contains("email adapter not attached"),
                "unattached result must truthfully identify missing platform email adapter");

        System.out.println("EmailCompositionToolContractTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
