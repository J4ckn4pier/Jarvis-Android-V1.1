package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** A deliberately RED finished-product blocker must not mask regressions in otherwise executable contracts. */
public final class KnownBlockerRunnerOrderingContractTest {
    public static void main(String[] args) throws Exception {
        String runner = Files.readString(Path.of("run-tests.sh"));
        int composedGate = runner.lastIndexOf("java -cp out com.jarvis.brain.BrainExitGateAcceptanceTest");
        int telephonyBlocker = runner.lastIndexOf("java -cp out com.jarvis.brain.AndroidConversationalCallBindingContractTest");

        check(composedGate >= 0, "runner must explicitly execute the composed brain exit gate");
        check(telephonyBlocker >= 0, "runner must explicitly retain the real conversational-call blocker");
        check(telephonyBlocker > composedGate,
                "known telephony blocker must execute after the full composed gate so it cannot mask unrelated regressions");

        System.out.println("KnownBlockerRunnerOrderingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
