package com.jarvis.brain;

import java.util.Set;

public final class ToolExecutionClassTest {
    public static void main(String[] args) {
        standardRegistryClassifiesResearchByMetadata();
        classificationIsNotDerivedFromToolName();
        consequentialFlagAndExecutionClassCannotDisagree();
        System.out.println("ToolExecutionClassTest passed");
    }

    private static void standardRegistryClassifiesResearchByMetadata() {
        ToolRegistry registry = ToolRegistry.standard();
        assertEquals(ToolExecutionClass.AUTONOMOUS_RESEARCH, registry.resolve("discover_places").orElseThrow().spec().executionClass(), "discover places class");
        assertEquals(ToolExecutionClass.AUTONOMOUS_RESEARCH, registry.resolve("weather_lookup").orElseThrow().spec().executionClass(), "weather class");
        assertEquals(ToolExecutionClass.DEVICE_REFLEX, registry.resolve("navigate").orElseThrow().spec().executionClass(), "navigation remains device reflex");
        assertEquals(ToolExecutionClass.CONSEQUENTIAL, registry.resolve("send_message").orElseThrow().spec().executionClass(), "send message remains consequential");
    }

    private static void classificationIsNotDerivedFromToolName() {
        ToolSpec oddlyNamedResearch = new ToolSpec("future_capability", false, Set.of(), Set.of(), "Safe research", ToolExecutionClass.AUTONOMOUS_RESEARCH);
        assertEquals(ToolExecutionClass.AUTONOMOUS_RESEARCH, oddlyNamedResearch.executionClass(), "metadata must classify new research tools without AssistantCore name lists");
    }

    private static void consequentialFlagAndExecutionClassCannotDisagree() {
        assertThrows(() -> new ToolSpec("bad_reflex", true, Set.of(), Set.of(), "bad", ToolExecutionClass.DEVICE_REFLEX),
                "consequential=true cannot be a reflex");
        assertThrows(() -> new ToolSpec("bad_consequential", false, Set.of(), Set.of(), "bad", ToolExecutionClass.CONSEQUENTIAL),
                "CONSEQUENTIAL execution class cannot claim consequential=false");
    }

    private static void assertThrows(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + ": expected IllegalArgumentException");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
    }
}
