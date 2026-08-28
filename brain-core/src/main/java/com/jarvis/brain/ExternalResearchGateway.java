package com.jarvis.brain;

import java.util.Map;

/**
 * Provider-neutral boundary for fresh external information used by the executive loop.
 * Platform/network implementations live outside the reasoning core and return structured
 * ToolResult values so failure/uncertainty remains visible to the cortex.
 */
public interface ExternalResearchGateway {
    ToolResult discoverPlaces(Map<String, String> arguments, ExecutionContext context);
    ToolResult resolveBusiness(Map<String, String> arguments, ExecutionContext context);
    ToolResult weatherLookup(Map<String, String> arguments, ExecutionContext context);

    static ExternalResearchGateway unavailable() {
        return new ExternalResearchGateway() {
            @Override public ToolResult discoverPlaces(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.failure("place discovery adapter not attached");
            }

            @Override public ToolResult resolveBusiness(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.failure("business resolution adapter not attached");
            }

            @Override public ToolResult weatherLookup(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.failure("weather adapter not attached");
            }
        };
    }
}
