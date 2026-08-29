package com.jarvis.brain;

import java.util.List;

public record ReasoningRequest(String utterance, String context, List<ToolSpec> tools) {
    public ReasoningRequest {
        context = context == null ? "" : context.trim();
        String style = ResponseStyleContract.beta().guidance();
        if (!context.contains(style)) {
            context = context.isBlank()
                    ? "Response style:\n" + style
                    : context + "\nResponse style:\n" + style;
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
