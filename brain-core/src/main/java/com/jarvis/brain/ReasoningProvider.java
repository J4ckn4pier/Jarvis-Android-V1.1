package com.jarvis.brain;

public interface ReasoningProvider {
    String id();
    boolean available();
    ReasoningResult reason(ReasoningRequest request);
}
