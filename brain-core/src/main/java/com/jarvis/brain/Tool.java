package com.jarvis.brain;

import java.util.Map;

@FunctionalInterface
public interface Tool {
    ToolResult execute(Map<String, String> arguments, ExecutionContext context);
}
