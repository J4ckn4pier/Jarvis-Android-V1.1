package com.jarvis.brain;

import java.util.List;

public final class ProviderRouter {
    private final List<ReasoningProvider> providers;

    public ProviderRouter(List<ReasoningProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public ReasoningResult reason(ReasoningRequest request) {
        RuntimeException last = null;
        for (ReasoningProvider provider : providers) {
            if (!provider.available()) continue;
            try {
                ReasoningResult result = provider.reason(request);
                if (result != null) return result;
            } catch (RuntimeException failure) {
                last = failure;
            }
        }
        if (last != null) throw last;
        return new ReasoningResult("none", "No reasoning provider is currently available.", null);
    }
}
