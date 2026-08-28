package com.jarvis.brain;

@FunctionalInterface
public interface ModelPlanGenerator {
    String generate(String prompt);
}
