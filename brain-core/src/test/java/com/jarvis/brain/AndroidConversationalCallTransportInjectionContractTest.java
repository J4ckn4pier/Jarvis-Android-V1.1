package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android registry construction must accept a provider-neutral conversational-call transport without faking a default. */
public final class AndroidConversationalCallTransportInjectionContractTest {
    public static void main(String[] args) throws Exception {
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));

        check(factory.contains("ConversationalCallTransport"),
                "Android registry factory must expose the provider-neutral conversational-call transport type");
        check(factory.contains("ToolRegistry.standard(research, callTransport)"),
                "Android registry factory must inject the supplied duplex transport into the shared brain registry");
        check(factory.contains("return create(context, research, null)"),
                "existing Android factory path must remain explicitly unattached until a real transport is configured");

        System.out.println("AndroidConversationalCallTransportInjectionContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
