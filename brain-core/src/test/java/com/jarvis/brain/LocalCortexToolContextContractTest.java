package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** A general cortex cannot choose Android actions reliably if it sees only tool names and no argument/meaning contract. */
public final class LocalCortexToolContextContractTest {
    public static void main(String[] args) throws Exception {
        Path providers = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers");
        String schema = Files.readString(providers.resolve("ProviderSharedPlanSchema.java"));
        String compatible = Files.readString(providers.resolve("OpenAiCompatibleChatProvider.java"));

        check(schema.contains("systemPrompt(ToolRegistry tools)"),
                "provider prompt must be built from the actual shared tool registry");
        check(schema.contains("requiredArguments()"),
                "provider prompt must tell the model each tool's required arguments");
        check(schema.contains("description()"),
                "provider prompt must tell the model what each tool actually does");
        check(compatible.contains("ProviderSharedPlanSchema.systemPrompt(tools)"),
                "the free local OpenAI-compatible cortex must receive the full tool semantics prompt");
        System.out.println("LocalCortexToolContextContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
