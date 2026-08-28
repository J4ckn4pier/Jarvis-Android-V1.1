package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ToolContractSelectorTest {
    private static int checks;

    public static void main(String[] args) {
        relevantToolsWinBoundedContextBudget();
        aliasesAndRequiredArgumentsContributeToRelevance();
        noMatchStillReturnsDeterministicBoundedFallback();
        System.out.println("ToolContractSelectorTest: " + checks + " assertions passed");
    }

    private static void relevantToolsWinBoundedContextBudget() {
        List<ToolSpec> tools = manyTools();
        List<ToolSpec> selected = ToolContractSelector.select("text Mom that I'm late", "recipient Mom", tools, 6);
        check(selected.size() <= 6, "local prompt tool contracts must respect hard budget");
        check(selected.stream().anyMatch(t -> t.name().equals("send_message")), "message intent must retain send_message contract");
        check(selected.stream().anyMatch(t -> t.name().equals("contact_lookup")), "recipient context should keep contact resolver relevant");
    }

    private static void aliasesAndRequiredArgumentsContributeToRelevance() {
        List<ToolSpec> tools = List.of(
                new ToolSpec("route", false, Set.of("directions", "take me"), Set.of("destination"), "Navigate somewhere"),
                new ToolSpec("unrelated", false, Set.of("foo"), Set.of("bar"), "Other"));
        List<ToolSpec> selected = ToolContractSelector.select("take me to the museum", "destination museum", tools, 1);
        check(selected.size() == 1 && selected.get(0).name().equals("route"), "alias/required-arg terms must influence relevance scoring");
    }

    private static void noMatchStillReturnsDeterministicBoundedFallback() {
        List<ToolSpec> selected = ToolContractSelector.select("let's talk about philosophy", "", manyTools(), 4);
        check(selected.size() == 4, "no-match fallback should still expose a small deterministic capability sample");
        List<ToolSpec> again = ToolContractSelector.select("let's talk about philosophy", "", manyTools(), 4);
        check(selected.stream().map(ToolSpec::name).toList().equals(again.stream().map(ToolSpec::name).toList()),
                "fallback selection must be deterministic for reproducible prompts/tests");
    }

    private static List<ToolSpec> manyTools() {
        ArrayList<ToolSpec> tools = new ArrayList<>();
        tools.add(new ToolSpec("send_message", true, Set.of("text", "message"), Set.of("recipient", "message"), "Send an external message"));
        tools.add(new ToolSpec("contact_lookup", false, Set.of("find contact", "who is"), Set.of("recipient"), "Resolve a saved contact"));
        for (int i = 0; i < 30; i++) tools.add(new ToolSpec("generic_" + i, false, Set.of("generic alias " + i), Set.of(), "Generic capability " + i));
        return tools;
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
