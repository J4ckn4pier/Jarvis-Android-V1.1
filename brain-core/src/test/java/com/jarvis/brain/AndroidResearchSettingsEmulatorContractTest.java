package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android 16 must render the live-research controls that configure the production research gateway. */
public final class AndroidResearchSettingsEmulatorContractTest {
    public static void main(String[] args) throws Exception {
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));

        check(smoke.contains("$PACKAGE/.SettingsActivity"),
                "Android smoke must launch the real JARVIS Settings screen");
        check(smoke.contains("LIVE RESEARCH"),
                "Android smoke must verify the live-research section is rendered on-device");
        check(smoke.contains("Research endpoint"),
                "Android smoke must verify the research endpoint control is rendered on-device");
        check(smoke.contains("SAVE RESEARCH ENDPOINT"),
                "Android smoke must verify the research endpoint can be saved from the phone UI");
        check(smoke.contains("jarvis-settings-ui.xml"),
                "Android smoke must preserve the Settings accessibility-tree evidence");

        System.out.println("AndroidResearchSettingsEmulatorContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
