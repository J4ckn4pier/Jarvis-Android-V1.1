package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Configured Android cortex proposals and diagnostics must use only the shared typed plan/validation path. */
public final class AndroidCortexSharedPlanContractTest {
    public static void main(String[] args) throws Exception {
        Path providers = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers");
        Path legacyCore = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/core");
        Path diagnosticsSmokePath = Path.of("../.github/scripts/diagnostics-smoke.sh");
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String diagnostics = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/DiagnosticsActivity.java"));
        String provider = Files.readString(providers.resolve("CortexProvider.java"));
        String openAi = Files.readString(providers.resolve("OpenAIResponsesProvider.java"));
        String anthropic = Files.readString(providers.resolve("AnthropicMessagesProvider.java"));
        String factory = Files.readString(providers.resolve("CortexProviderFactory.java"));
        String workflow = Files.readString(Path.of("../.github/workflows/build-apk.yml"));
        String mainManifest = Files.readString(Path.of("../android/app/src/main/AndroidManifest.xml"));
        String debugManifest = Files.readString(Path.of("../android/app/src/debug/AndroidManifest.xml"));

        check(runtime.contains("reasonWithConfiguredCortex(app, request, tools)"),
                "Android runtime must give provider reasoning the shared request and tool registry");
        check(runtime.contains("provider.proposeReasoning(request, tools)"),
                "Android runtime must enter the provider-neutral shared reasoning path");
        check(provider.contains("ReasoningResult proposeReasoning(ReasoningRequest request, ToolRegistry tools)"),
                "cortex interface must require the shared typed reasoning path");
        check(!provider.contains("IntentPlan") && !provider.contains("propose(String utterance)"),
                "cortex interface must not expose the retired Android intent-plan compatibility path");
        check(!openAi.contains("IntentPlan") && !openAi.contains("ProviderSchema") && !openAi.contains("ProviderPlanFactory"),
                "OpenAI provider must not carry retired raw intent-plan compatibility code");
        check(!anthropic.contains("IntentPlan") && !anthropic.contains("ProviderSchema") && !anthropic.contains("ProviderPlanFactory"),
                "Anthropic provider must not carry retired raw intent-plan compatibility code");
        check(factory.contains("ReasoningResult proposeReasoning(ReasoningRequest request, ToolRegistry tools)"),
                "local cortex fallback must implement the same shared typed interface");
        check(!Files.exists(providers.resolve("CortexPlanAdapter.java")),
                "retired cortex compatibility adapter must not ship");
        check(!Files.exists(providers.resolve("ProviderSchema.java")) && !Files.exists(providers.resolve("ProviderPlanFactory.java")),
                "retired provider intent schema/factory must not ship");
        check(!Files.exists(legacyCore),
                "retired Android-local intent engine package must not ship after shared brain migration");
        check(!runtime.contains("proposed.answer(), null"),
                "resolved cortex proposals must not silently discard their action plan");
        check(!diagnostics.contains("com.jarvis.mobile.brain.core") && !diagnostics.contains("LocalIntentEngine") && !diagnostics.contains("IntentPlan"),
                "user-visible diagnostics must not depend on the retired Android-local intent engine");
        check(diagnostics.contains("AndroidToolRegistryFactory.create") && diagnostics.contains("registry.resolve"),
                "diagnostics must inspect the actual production typed tool registry without executing actions");
        check(Files.exists(diagnosticsSmokePath),
                "Android CI must include an emulator smoke for the real diagnostics screen");
        String diagnosticsSmoke = Files.readString(diagnosticsSmokePath);
        check(diagnosticsSmoke.contains("com.jarvis.mobile.DiagnosticsActivity")
                        && diagnosticsSmoke.contains("Production typed-tool self-test")
                        && diagnosticsSmoke.contains("Autonomous conversational calls")
                        && diagnosticsSmoke.contains("Diagnostics inspect capability registration only"),
                "diagnostics emulator smoke must directly launch and prove truthful typed-tool and telephony status text is rendered");
        check(mainManifest.contains("<activity android:name=\".DiagnosticsActivity\" android:exported=\"false\"")
                        && debugManifest.contains("android:name=\"com.jarvis.mobile.DiagnosticsActivity\"")
                        && debugManifest.contains("android:exported=\"true\""),
                "diagnostics Activity must remain private in production and be exported only by the debug test manifest");
        check(workflow.contains("diagnostics-smoke.sh"),
                "full APK workflow must launch the user-visible diagnostics screen on Android 16");

        System.out.println("AndroidCortexSharedPlanContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
