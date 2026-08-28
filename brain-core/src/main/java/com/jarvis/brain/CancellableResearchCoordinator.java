package com.jarvis.brain;

import java.util.Map;

/**
 * Provider-neutral lifecycle wrapper for research that may be revised mid-flight.
 * A same-goal correction cancels the prior request before reusing the same gateway
 * with revised parameters. Network-specific cancellation tokens live in adapters.
 */
public final class CancellableResearchCoordinator {
    private final ExternalResearchGateway gateway;
    private final Runnable cancelInFlight;
    private boolean placesStarted;

    public CancellableResearchCoordinator(ExternalResearchGateway gateway, Runnable cancelInFlight) {
        if (gateway == null) throw new IllegalArgumentException("research gateway required");
        this.gateway = gateway;
        this.cancelInFlight = cancelInFlight == null ? () -> {} : cancelInFlight;
    }

    public synchronized ToolResult beginPlaces(Map<String, String> arguments, ExecutionContext context) {
        placesStarted = true;
        return gateway.discoverPlaces(arguments == null ? Map.of() : Map.copyOf(arguments), context);
    }

    public synchronized ToolResult restartPlaces(Map<String, String> revisedArguments, ExecutionContext context) {
        if (placesStarted) cancelInFlight.run();
        placesStarted = true;
        return gateway.discoverPlaces(revisedArguments == null ? Map.of() : Map.copyOf(revisedArguments), context);
    }
}
