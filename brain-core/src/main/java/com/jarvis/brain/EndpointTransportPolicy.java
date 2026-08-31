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
                    || isPrivateIpv4(host)
                    || (host.endsWith(".local") && host.length() > ".local".length());
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                if (parts[i].isEmpty() || parts[i].length() > 3) return false;
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) return false;
            }
        } catch (NumberFormatException invalid) {
            return false;
        }
        if (octets[0] == 10) return true;
        if (octets[0] == 192 && octets[1] == 168) return true;
        return octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31;
    }
}
