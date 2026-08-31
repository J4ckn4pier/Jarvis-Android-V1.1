package com.jarvis.mobile.brain.providers;

import com.jarvis.brain.EndpointTransportPolicy;

/** Keeps raw transport-security policy out of the normal user Settings surface. */
public final class LocalAiEndpointPolicy {
    private LocalAiEndpointPolicy() {}

    public static boolean allows(String endpoint) {
        return EndpointTransportPolicy.allows(endpoint);
    }
}
