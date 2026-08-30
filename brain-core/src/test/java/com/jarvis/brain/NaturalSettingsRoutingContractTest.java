package com.jarvis.brain;

/** APK sprint regression: conversational lead-in must not hide the actual settings command. */
public final class NaturalSettingsRoutingContractTest {
    public static void main(String[] args) throws Exception {
        SemanticGoalInterpreter interpreter = new SemanticGoalInterpreter();
        Plan plan = interpreter.interpret("I'm good. Can you do me a favor and open settings, please?")
                .orElseThrow(() -> new AssertionError("natural settings request must route locally without an AI provider"));
        check(plan.steps().size() == 1, "settings request should be one deterministic local action");
        check("open_jarvis_settings".equals(plan.steps().get(0).tool()),
                "settings request must open JARVIS settings, not guess an installed app or require a provider");
        check(!plan.steps().get(0).consequential(), "opening JARVIS settings must not require approval");
        UserFacingSettingsContractTest.main(args);
        System.out.println("NaturalSettingsRoutingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
