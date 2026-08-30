package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android 16 smoke must prove consequential decisions render as real controls and cancellation clears them. */
public final class AndroidDecisionAffordanceEmulatorContractTest {
    public static void main(String[] args) throws Exception {
        String activity = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));

        check(activity.contains("primaryActionButton.setContentDescription(\"JARVIS \" + view.primaryAction().name() + \" action\");"),
                "primary runtime decision control needs a stable accessibility identifier for device verification");
        check(activity.contains("secondaryActionButton.setContentDescription(\"JARVIS \" + view.secondaryAction().name() + \" action\");"),
                "secondary runtime decision control needs a stable accessibility identifier for device verification");
        check(smoke.contains("dump_ui_retry()") && smoke.contains("uiautomator dump") && smoke.contains("UI_DUMP_READY"),
                "emulator smoke must retry transient Android accessibility-tree publication failures instead of failing a healthy app on one null-root read");
        check(smoke.contains("--es jarvis_test_command '\"Jarvis, text Mom I am on my way\"'"),
                "emulator smoke must drive a deterministic consequential message request through MainActivity");
        check(smoke.contains("JARVIS_RUNTIME_OUTPUT state=AWAITING_APPROVAL"),
                "emulator smoke must prove the runtime reaches pending approval before interacting with controls");
        check(smoke.contains("DECISION_CONTROLS_READY=0"),
                "emulator smoke must wait for Android accessibility publication after runtime approval state");
        check(smoke.contains("jarvis-decision-ui-attempt-"),
                "emulator smoke must retain per-attempt UI evidence while waiting for decision controls");
        check(smoke.contains("decision activity left foreground before controls could be inspected"),
                "emulator smoke must distinguish foreground loss from missing decision controls");
        check(smoke.contains("JARVIS APPROVE action"),
                "emulator smoke must prove the primary approval control is in the Android UI tree");
        check(smoke.contains("JARVIS CANCEL action"),
                "emulator smoke must prove the cancellation control is in the Android UI tree");
        check(smoke.contains("JARVIS_SHARED_BRAIN_ACTIVE.*state=IDLE"),
                "emulator smoke must prove tapping CANCEL clears the pending decision through the shared runtime surface");

        System.out.println("AndroidDecisionAffordanceEmulatorContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
