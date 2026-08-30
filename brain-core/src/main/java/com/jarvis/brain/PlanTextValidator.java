package com.jarvis.brain;

@FunctionalInterface
public interface PlanTextValidator {
    PlanValidation validate(String modelPlanText);
}
