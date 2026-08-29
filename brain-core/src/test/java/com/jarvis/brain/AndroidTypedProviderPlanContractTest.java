package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android cloud cortexes must propose shared typed tool plans; provider output never owns approval policy. */
public final class AndroidTypedProviderPlanContractTest {
    public static void main(String[] args) throws Exception {
        String provider = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/CortexProvider.java"));
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String schema = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/ProviderSharedPlanSchema.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/ProviderSharedPlanFactory.java"));
        String openAi = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/OpenAIResponsesProvider.java"));
        String anthropic = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/AnthropicMessagesProvider.java"));

        check(provider.contains("proposeReasoning(ReasoningRequest request, ToolRegistry tools)"),
                "cortex interface must expose the shared reasoning request + tool registry path");
        check(runtime.contains("provider.proposeReasoning(request, tools)"),
                "Android runtime must consume typed shared reasoning directly from the provider");

        check(schema.contains("\"steps\"") && schema.contains("\"tool\"") && schema.contains("\"arguments\""),
                "typed provider schema must express shared tool steps and structured arguments");
        check(!schema.contains("requiresApproval") && !schema.contains("requires_approval"),
                "provider schema must not let the model decide approval policy");
        check(schema.contains("tools.specs()"),
                "typed provider schema must derive allowed tool names from the current shared registry");

        check(factory.contains("new PlanValidator(tools).validate"),
                "typed provider plans must pass through the shared PlanValidator");
        check(factory.contains("validation.effectivePlan()"),
                "typed provider plan execution must use the validator's effective plan");
        check(factory.contains("Map<String, String>"),
                "typed provider arguments must become shared string argument maps");

        check(openAi.contains("ProviderSharedPlanSchema.jsonSchema(tools)"),
                "OpenAI cortex must request the shared typed plan schema");
        check(openAi.contains("ProviderSharedPlanFactory.fromJson"),
                "OpenAI cortex must parse into validated shared ReasoningResult");
        check(anthropic.contains("ProviderSharedPlanSchema.jsonSchema(tools)"),
                "Anthropic cortex must request the shared typed plan schema");
        check(anthropic.contains("ProviderSharedPlanFactory.fromJson"),
                "Anthropic cortex must parse into validated shared ReasoningResult");

        System.out.println("AndroidTypedProviderPlanContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
