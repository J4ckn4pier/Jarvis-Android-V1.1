package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** The default zero-metered-cost assistant path must support a user-owned LAN cortex and general language reasoning. */
public final class LocalAssistantCortexContractTest {
    public static void main(String[] args) throws Exception {
        check(EndpointTransportPolicy.allows("http://192.168.1.25:11434/v1/chat/completions"),
                "private RFC1918 LAN cortex endpoints must be allowed over HTTP");
        check(EndpointTransportPolicy.allows("http://10.1.2.3:8080/v1/chat/completions"),
                "10/8 private LAN cortex endpoints must be allowed over HTTP");
        check(EndpointTransportPolicy.allows("http://172.20.10.4:8080/v1/chat/completions"),
                "172.16/12 private LAN cortex endpoints must be allowed over HTTP");
        check(!EndpointTransportPolicy.allows("http://8.8.8.8:8080/v1/chat/completions"),
                "public plain-HTTP cortex endpoints must remain blocked");

        String schema = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/ProviderSharedPlanSchema.java"));
        check(schema.contains("ordinary natural language"),
                "provider prompt must explicitly treat arbitrary ordinary language as understandable");
        check(schema.contains("tools are abilities") || schema.contains("tools are capabilities"),
                "provider prompt must state that tool inventory does not limit what JARVIS can understand");

        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/CortexProviderFactory.java"));
        check(factory.contains("MODE_OPENAI_COMPATIBLE"), "local OpenAI-compatible cortex mode must remain available");
        check(factory.contains("no API key") || factory.contains("no api key") || factory.contains("without an API key"),
                "local cortex configuration must document that no API key is required");
        System.out.println("LocalAssistantCortexContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
