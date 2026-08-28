package com.jarvis.brain;

import java.util.Set;

public final class ToolExecutionClassTest {
    public static void main(String[] args) {
        standardRegistryClassifiesResearchByMetadata();
        classificationIsNotDerivedFromToolName();
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

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
    }
}
