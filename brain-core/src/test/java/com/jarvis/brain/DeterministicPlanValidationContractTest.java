package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;

/** Built-in deterministic plans must obey the same validation and canonicalization as provider plans. */
public final class DeterministicPlanValidationContractTest {
    public static void main(String[] args) throws Exception {
        malformedDeterministicPlansStillUseClarificationValidation();
        validDeterministicPlansReturnValidatorEffectivePlan();
        System.out.println("DeterministicPlanValidationContractTest passed");
    }

    private static void malformedDeterministicPlansStillUseClarificationValidation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/jarvis/brain/AssistantCore.java"));
        check(source.contains("if(!v.valid())return rememberedResponse(validatedPlanResponse(response.plan(),response.text()"),
                "deterministic ACTION_PLAN responses must preserve validation while retaining any clarification response");
        check(source.contains("executionClassifier.isPureAutonomousResearch(v.effectivePlan())"),
                "valid deterministic research plans must keep their autonomous executive path");
    }

    private static void validDeterministicPlansReturnValidatorEffectivePlan() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec(
                "phone_control",
                true,
                Set.of("open_dialer"),
                Set.of(),
                "canonical phone control",
                ToolExecutionClass.CONSEQUENTIAL),
                (arguments, context) -> ToolResult.success("opened"));
        ReasoningRouter unused = request -> new ReasoningResult("unused", "unused", null);
        AssistantCore assistant = new AssistantCore(BrainEngine.createDefault(Clock.systemUTC()), unused, registry);

        BrainResponse response = assistant.handle("Jarvis phone app");
        check(response.kind() == BrainResponse.Kind.ACTION_PLAN && response.plan() != null,
                "built-in dialer reflex should remain an action plan after validation");
        PlanStep step = response.plan().steps().get(0);
        check(step.tool().equals("phone_control"),
                "valid deterministic plan must return the validator's canonical tool name, not the pre-validation alias");
        check(step.consequential(),
                "valid deterministic plan must preserve safety elevation from the registered tool specification");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
