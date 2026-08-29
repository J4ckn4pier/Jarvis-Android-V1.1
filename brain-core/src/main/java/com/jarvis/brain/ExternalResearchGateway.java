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

    /** Fresh menu/dish/price evidence. Default keeps older adapters source-compatible and fail-closed. */
    default ToolResult getMenu(Map<String, String> arguments, ExecutionContext context) {
        return ToolResult.failure("menu adapter not attached");
    }

    /** Translation is provider-neutral and must fail closed when no language adapter is attached. */
    default ToolResult translate(Map<String, String> arguments, ExecutionContext context) {
        return ToolResult.failure("translation adapter not attached");
    }

    /**
     * Best-effort online reservation flow. Implementations must report the actual outcome:
     * confirmed time, real available alternatives, or a failure reason. Never infer success.
     */
    default ToolResult attemptReservation(Map<String, String> arguments, ExecutionContext context) {
        return ToolResult.failure("reservation adapter not attached");
    }

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
