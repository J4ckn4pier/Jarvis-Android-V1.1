package com.jarvis.brain;

@FunctionalInterface
public interface LocalCortexTransport {
    String send(String requestBody);
}
