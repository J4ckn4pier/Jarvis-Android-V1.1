package com.jarvis.brain;

import java.util.List;

public record ReasoningRequest(String utterance, String context, List<ToolSpec> tools) {
    public ReasoningRequest {
        context = context == null ? "" : context;
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
