package com.jarvis.brain;

@FunctionalInterface
public interface ReasoningRouter {
    ReasoningResult reason(ReasoningRequest request);
}
