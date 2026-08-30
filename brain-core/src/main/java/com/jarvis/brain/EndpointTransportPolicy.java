package com.jarvis.brain;

import java.net.URI;
import java.util.Locale;

/** Shared fail-closed transport policy for user-configured JARVIS endpoints. */
public final class EndpointTransportPolicy {
    private EndpointTransportPolicy() {}

    public static boolean allows(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return false;
        try {
            URI uri = URI.create(endpoint.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isBlank()) return false;
            if ("https".equals(scheme)) return true;
            if (!"http".equals(scheme)) return false;
            return host.equals("localhost")
                    || host.equals("127.0.0.1")
                    || host.equals("10.0.2.2")
                    || (host.endsWith(".local") && host.length() > ".local".length());
        } catch (RuntimeException invalid) {
            return false;
        }
    }
}
