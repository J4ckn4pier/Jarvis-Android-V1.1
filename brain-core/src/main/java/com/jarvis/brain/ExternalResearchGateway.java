package com.jarvis.brain;

import java.util.Map;

/**
 * Provider-neutral boundary for fresh external information and evidence-backed orchestration
 * used by the executive loop. Platform/network/model implementations live outside the reasoning
 * core and return structured ToolResult values so failure/uncertainty remains visible to cortex.
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

    /** Rank real candidate options using current user/context evidence. Never synthesize readiness. */
    default ToolResult rankOptions(Map<String, String> arguments, ExecutionContext context) {
        return ToolResult.failure("ranking adapter not attached");
    }

    /** Turn ranked evidence into a user-facing option presentation. Never synthesize readiness. */
    default ToolResult presentOptions(Map<String, String> arguments, ExecutionContext context) {
        return ToolResult.failure("presentation adapter not attached");
    }

    /** Report an actual completed multi-step outcome from evidence in the current execution. */
    default ToolResult reportOutcome(Map<String, String> arguments, ExecutionContext context) {
        return ToolResult.failure("outcome reporting adapter not attached");
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
