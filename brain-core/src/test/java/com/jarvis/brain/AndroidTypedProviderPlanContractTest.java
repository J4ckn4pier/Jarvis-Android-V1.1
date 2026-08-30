package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android cortexes must propose shared typed tool plans; provider output never owns approval policy. */
public final class AndroidTypedProviderPlanContractTest {
    public static void main(String[] args) throws Exception {
        Path providers = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers");
        String provider = Files.readString(providers.resolve("CortexProvider.java"));
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String schema = Files.readString(providers.resolve("ProviderSharedPlanSchema.java"));
        String planFactory = Files.readString(providers.resolve("ProviderSharedPlanFactory.java"));
        String openAi = Files.readString(providers.resolve("OpenAIResponsesProvider.java"));
        String anthropic = Files.readString(providers.resolve("AnthropicMessagesProvider.java"));
        String compatible = Files.readString(providers.resolve("OpenAiCompatibleChatProvider.java"));
        String cortexFactory = Files.readString(providers.resolve("CortexProviderFactory.java"));
        String settings = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));

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

        check(planFactory.contains("new PlanValidator(tools).validate"),
                "typed provider plans must pass through the shared PlanValidator");
        check(planFactory.contains("validation.effectivePlan()"),
                "typed provider plan execution must use the validator's effective plan");
        check(planFactory.contains("Map<String, String>"),
                "typed provider arguments must become shared string argument maps");

        check(openAi.contains("ProviderSharedPlanSchema.jsonSchema(tools)") && openAi.contains("ProviderSharedPlanFactory.fromJson"),
                "OpenAI cortex must use the shared typed schema and validated shared plan factory");
        check(anthropic.contains("ProviderSharedPlanSchema.jsonSchema(tools)") && anthropic.contains("ProviderSharedPlanFactory.fromJson"),
                "Anthropic cortex must use the shared typed schema and validated shared plan factory");
        check(compatible.contains("ProviderSharedPlanSchema.jsonSchema(tools)")
                        && compatible.contains("ProviderSharedPlanFactory.fromJson")
                        && compatible.contains("ProviderReasoningEnvelope.userContent(request)"),
                "OpenAI-compatible local cortex must use the same shared typed schema, context envelope, and validator path");
        check(compatible.contains("/v1/chat/completions") && compatible.contains("response_format"),
                "OpenAI-compatible local cortex must target the broadly supported chat-completions structured-output path");
        check(!compatible.contains("isEmpty() && !apiKey.isEmpty()"),
                "local-compatible cortex must not require a paid-provider API key");
        check(compatible.contains("EndpointTransportPolicy.allows(endpoint)")
                        && compatible.contains("!model.isEmpty() && EndpointTransportPolicy.allows(endpoint)"),
                "local-compatible cortex must not report itself configured when its endpoint violates the shared transport policy");
        check(cortexFactory.contains("MODE_OPENAI_COMPATIBLE") && cortexFactory.contains("new OpenAiCompatibleChatProvider"),
                "Android provider factory must expose the free/local OpenAI-compatible cortex mode");
        check(cortexFactory.contains("needs a model and allowed endpoint"),
                "human-facing local cortex status must identify both missing model and endpoint readiness instead of blaming only the model");
        check(settings.contains("MODE_OPENAI_COMPATIBLE") && settings.contains("OpenAI-compatible local endpoint"),
                "Android settings must let the user select the free/local compatible cortex explicitly");

        System.out.println("AndroidTypedProviderPlanContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
