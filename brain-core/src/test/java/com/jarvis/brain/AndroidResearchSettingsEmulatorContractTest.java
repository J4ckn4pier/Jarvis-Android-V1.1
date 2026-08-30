package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android 16 must render raw live-research controls only on the advanced Developer Options surface. */
public final class AndroidResearchSettingsEmulatorContractTest {
    public static void main(String[] args) throws Exception {
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));

        check(smoke.contains("$PACKAGE/.DeveloperSettingsActivity"),
                "Android smoke must launch Developer Options for raw research configuration");
        check(!researchSection(smoke).contains("$PACKAGE/.SettingsActivity"),
                "raw research endpoint smoke must not use the normal user Settings surface");
        check(smoke.contains("DEVELOPER OPTIONS"),
                "Android smoke must verify the advanced surface is rendered on-device");
        check(smoke.contains("Research endpoint"),
                "Android smoke must verify the research endpoint control is rendered on-device");
        check(smoke.contains("SAVE RESEARCH ENDPOINT"),
                "Android smoke must verify the research endpoint can be saved from Developer Options");
        check(smoke.contains("jarvis-developer-settings-ui.xml"),
                "Android smoke must preserve Developer Options accessibility-tree evidence");

        System.out.println("AndroidResearchSettingsEmulatorContractTest passed");
    }

    private static String researchSection(String smoke) {
        int start = smoke.indexOf("# Prove live-research configuration");
        int end = smoke.indexOf("# Prove Android owns the assistant selection");
        if (start < 0) return smoke;
        return end > start ? smoke.substring(start, end) : smoke.substring(start);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
