package com.jarvis.brain;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlanExecutionClassifierTest {
    public static void main(String[] args) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("novel_research_tool", false, Set.of(), Set.of(), "A newly added research capability", ToolExecutionClass.AUTONOMOUS_RESEARCH),
                (arguments, context) -> ToolResult.success("ok"));
        registry.register(new ToolSpec("novel_device_tool", false, Set.of(), Set.of(), "A newly added device reflex", ToolExecutionClass.DEVICE_REFLEX),
                (arguments, context) -> ToolResult.success("ok"));

        PlanExecutionClassifier classifier = new PlanExecutionClassifier(registry);
        Plan research = new Plan("research something new", List.of(new PlanStep("novel_research_tool", Map.of(), false)));
        Plan device = new Plan("do something locally", List.of(new PlanStep("novel_device_tool", Map.of(), false)));
        Plan mixed = new Plan("research then act", List.of(
                new PlanStep("novel_device_tool", Map.of(), false),
                new PlanStep("novel_research_tool", Map.of(), false)));
        Plan unknown = new Plan("unknown", List.of(new PlanStep("not_registered", Map.of(), false)));

        check(classifier.containsAutonomousResearch(research), "new research tools must route by ToolExecutionClass, not a hard-coded name list");
        check(!classifier.containsAutonomousResearch(device), "device reflex must not be promoted to research");
        check(classifier.containsAutonomousResearch(mixed), "mixed plan containing research must enter executive research loop");
        check(!classifier.containsAutonomousResearch(unknown), "unknown tools must not be treated as research");
        System.out.println("PlanExecutionClassifierTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
