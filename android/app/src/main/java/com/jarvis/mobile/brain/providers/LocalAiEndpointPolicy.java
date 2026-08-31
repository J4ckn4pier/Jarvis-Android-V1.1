package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.EndpointTransportPolicy;

import java.net.URI;
import java.util.Locale;

/** Keeps raw transport-security policy out of the normal user Settings surface. */
public final class LocalAiEndpointPolicy {
    private LocalAiEndpointPolicy() {}

    public static boolean allows(String endpoint) {
        if (!EndpointTransportPolicy.allows(endpoint)) return false;
        if (endpoint == null || endpoint.isBlank()) return false;
        try {
            String host = URI.create(endpoint.trim()).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || host.endsWith(".local");
        } catch (RuntimeException invalid) {
            return false;
        }
    }
}
