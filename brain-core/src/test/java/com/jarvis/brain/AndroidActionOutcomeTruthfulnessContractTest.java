package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Failed/clarifying Android hands must never be promoted to successful plan steps. */
public final class AndroidActionOutcomeTruthfulnessContractTest {
    public static void main(String[] args) throws Exception {
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));

        check(factory.contains("isFailureOutcome(lower)"),
                "Android tool results must use one centralized failure classifier");
        check(factory.contains("lower.startsWith(\"no compatible\")"),
                "missing compatible app must be a failed tool outcome");
        check(factory.contains("lower.startsWith(\"tell me\")"),
                "missing required user input must be a failed tool outcome");
        check(factory.contains("lower.startsWith(\"enable \")"),
                "permission/configuration remediation must be a failed tool outcome");
        check(factory.contains("lower.contains(\"unavailable\")"),
                "unavailable device capability must be a failed tool outcome");
        check(factory.contains("lower.contains(\"must be\")"),
                "invalid constrained input must be a failed tool outcome");
        check(factory.contains("lower.contains(\"too large\")"),
                "rejected oversized input must be a failed tool outcome");
        check(factory.contains("lower.contains(\"does not expose\")"),
                "missing hardware capability must be a failed tool outcome");
        check(factory.contains("lower.contains(\"couldn't\")") || factory.contains("lower.contains(\"couldn’t\")"),
                "explicit could-not outcomes must remain failures");
        check(factory.contains("return ToolResult.failure(result)"),
                "classified Android failures must be returned to the executive loop as ToolResult.failure");

        System.out.println("AndroidActionOutcomeTruthfulnessContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
