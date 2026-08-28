package com.jarvis.brain;

@FunctionalInterface
public interface AssistantContextSource {
    String contextFor(String utterance);

    static AssistantContextSource none() { return utterance -> ""; }
}
