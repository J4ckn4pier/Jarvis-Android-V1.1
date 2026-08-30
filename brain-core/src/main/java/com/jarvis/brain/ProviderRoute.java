package com.jarvis.brain;

public record ProviderRoute(ReasoningProvider provider, ProviderTier tier, int priority) {
    public ProviderRoute {
        if (provider == null) throw new IllegalArgumentException("provider required");
        tier = tier == null ? ProviderTier.PAID_EXTERNAL : tier;
    }
}
