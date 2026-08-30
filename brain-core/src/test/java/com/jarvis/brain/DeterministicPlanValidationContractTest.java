package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Built-in deterministic plans must obey the same required-argument validation as provider plans. */
public final class DeterministicPlanValidationContractTest {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/jarvis/brain/AssistantCore.java"));
        check(source.contains("if(!v.valid())return validatedPlanResponse(response.plan(),response.text()"),
                "deterministic ACTION_PLAN responses must not bypass failed PlanValidator results");
        check(source.contains("executionClassifier.isPureAutonomousResearch(v.effectivePlan())"),
                "valid deterministic research plans must keep their autonomous executive path");
        System.out.println("DeterministicPlanValidationContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
