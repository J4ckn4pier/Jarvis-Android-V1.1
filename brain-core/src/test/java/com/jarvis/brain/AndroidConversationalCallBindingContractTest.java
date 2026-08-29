package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** The architecture-promised telephony agent must never ship as the shared registry's unattached placeholder. */
public final class AndroidConversationalCallBindingContractTest {
    public static void main(String[] args) throws Exception {
        String standard = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String androidFactory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));

        check(standard.contains("\"place_conversational_call\""),
                "shared brain must expose the provider-independent conversational-call capability");
        check(standard.contains("telephony adapter not attached"),
                "shared fallback must remain explicit failure rather than synthetic success");
        check(androidFactory.contains("register(registry, \"place_conversational_call\""),
                "Android production registry must replace the unattached conversational-call placeholder");
        check(androidFactory.contains("ToolExecutionClass.CONSEQUENTIAL"),
                "Android conversational calling must remain consequential and approval-gated");

        System.out.println("AndroidConversationalCallBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
