package com.jarvis.brain;

import java.util.Set;

/** Potentially private context must stay out of provider prompts unless the utterance explicitly makes it relevant. */
public final class KeywordGatedAssistantContextSourceTest {
    private static int checks;

    public static void main(String[] args) {
        int[] reads = {0};
        AssistantContextSource privateSource = utterance -> {
            reads[0]++;
            return "Recent notifications:\n• Mail — Bank: statement ready";
        };
        KeywordGatedAssistantContextSource gated = new KeywordGatedAssistantContextSource(
                privateSource, Set.of("notification", "notifications", "what did i miss"));

        check(gated.contextFor("plan dinner tonight").isEmpty(), "unrelated request receives no private context");
        check(reads[0] == 0, "unrelated request does not even read the private source");

        String explicit = gated.contextFor("what notifications did I miss?");
        check(explicit.contains("Bank: statement ready"), "explicitly relevant request receives private context");
        check(reads[0] == 1, "relevant request reads the private source once");

        String phrase = gated.contextFor("what did I miss while driving?");
        check(phrase.contains("Recent notifications"), "multiword relevance phrase is supported");
        check(reads[0] == 2, "phrase match reads source once");

        System.out.println("KeywordGatedAssistantContextSourceTest: " + checks + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
