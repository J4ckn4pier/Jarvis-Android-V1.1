package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Configured Android cortex proposals must enter the shared typed plan/validation path, never bypass or disappear. */
public final class AndroidCortexSharedPlanContractTest {
    public static void main(String[] args) throws Exception {
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));
        String provider = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/CortexProvider.java"));
        String adapter = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/providers/CortexPlanAdapter.java"));

        check(runtime.contains("reasonWithConfiguredCortex(app, request, tools)"),
                "Android runtime must give provider reasoning the shared request and tool registry");
        check(runtime.contains("provider.proposeReasoning(request, tools)"),
                "Android runtime must enter the provider-neutral shared reasoning path");
        check(provider.contains("CortexPlanAdapter.toReasoningResult(this, propose(request.utterance()), tools)"),
                "legacy cortex providers must retain a conservative shared-plan compatibility bridge");
        check(!runtime.contains("proposed.answer(), null"),
                "resolved cortex proposals must not silently discard their action plan");
        check(adapter.contains("new PlanValidator(tools).validate"),
                "cortex bridge must validate every mapped plan against the shared tool registry");
        check(adapter.contains("validation.valid() ? validation.effectivePlan() : null"),
                "invalid/underspecified provider actions must fail closed and valid actions must use the validator's effective plan");
        check(adapter.contains("case SMS, EMAIL -> null"),
                "legacy free-form consequential communication payloads must fail closed until typed arguments exist");

        System.out.println("AndroidCortexSharedPlanContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
