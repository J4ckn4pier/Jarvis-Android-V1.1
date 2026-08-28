package com.jarvis.brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider router that preserves JARVIS's zero-surprise-cost policy and temporarily
 * suppresses repeatedly failing providers instead of hammering a broken cortex.
 */
public final class PolicyProviderRouter implements ReasoningRouter {
    private final List<ProviderRoute> routes;
    private final boolean allowPaid;
    private final int failureThreshold;
    private final Map<String, Integer> consecutiveFailures = new HashMap<>();

    public PolicyProviderRouter(List<ProviderRoute> routes, boolean allowPaid, int failureThreshold) {
        ArrayList<ProviderRoute> sorted = new ArrayList<>(routes == null ? List.of() : routes);
        sorted.sort(Comparator.comparing((ProviderRoute r) -> r.tier().ordinal())
                .thenComparingInt(ProviderRoute::priority));
        this.routes = List.copyOf(sorted);
        this.allowPaid = allowPaid;
        this.failureThreshold = Math.max(1, failureThreshold);
    }

    @Override
    public ReasoningResult reason(ReasoningRequest request) {
        for (ProviderRoute route : routes) {
            if (route.tier() == ProviderTier.PAID_EXTERNAL && !allowPaid) continue;
            ReasoningProvider provider = route.provider();
            if (!provider.available()) continue;
            if (consecutiveFailures.getOrDefault(provider.id(), 0) >= failureThreshold) continue;
            try {
                ReasoningResult result = provider.reason(request);
                if (result != null) {
                    consecutiveFailures.remove(provider.id());
                    return result;
                }
                consecutiveFailures.merge(provider.id(), 1, Integer::sum);
            } catch (RuntimeException failure) {
                consecutiveFailures.merge(provider.id(), 1, Integer::sum);
            }
        }
        return new ReasoningResult("none", "No permitted reasoning provider is currently available.", null);
    }

    public int failureCount(String providerId) {
        return consecutiveFailures.getOrDefault(providerId, 0);
    }
}
