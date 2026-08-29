package com.jarvis.brain;

import java.util.List;

/** Dynamic device/session context must compose with durable memory without polluting empty sections. */
public final class CompositeAssistantContextSourceTest {
    private static int checks;

    public static void main(String[] args) {
        AssistantContextSource source = new CompositeAssistantContextSource(List.of(
                utterance -> "memory: prefers quiet restaurants",
                utterance -> "",
                utterance -> "device: headphones connected",
                utterance -> null));

        String context = source.contextFor("plan dinner");
        check(context.contains("memory: prefers quiet restaurants"), "memory context retained");
        check(context.contains("device: headphones connected"), "dynamic device context retained");
        check(!context.contains("null"), "null source output omitted");
        check(!context.contains("\n\n\n"), "empty sources do not create noisy blank sections");

        AssistantContextSource none = new CompositeAssistantContextSource(List.of(
                utterance -> " ", utterance -> null));
        check(none.contextFor("anything").isEmpty(), "all-empty sources compose to empty context");

        System.out.println("CompositeAssistantContextSourceTest: " + checks + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
