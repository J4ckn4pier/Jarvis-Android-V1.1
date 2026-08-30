package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android 16 must prove a user-entered research endpoint persists across a Settings restart. */
public final class AndroidResearchSettingsPersistenceEmulatorContractTest {
    public static void main(String[] args) throws Exception {
        String settings = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));

        check(settings.contains("JARVIS research endpoint"),
                "research endpoint input needs a stable accessibility identity for real device interaction");
        check(settings.contains("JARVIS save research endpoint"),
                "research endpoint save action needs a stable accessibility identity for real device interaction");
        check(smoke.contains("http://127.0.0.1:8765/research"),
                "Android smoke must save a zero-cost local research endpoint rather than an external paid service");
        check(smoke.contains("jarvis-settings-saved-ui.xml"),
                "Android smoke must preserve accessibility evidence after saving the research endpoint");
        check(smoke.contains("jarvis-settings-reopened-ui.xml"),
                "Android smoke must reopen Settings and preserve evidence that the endpoint survived restart");
        check(smoke.contains("jarvis-research-endpoint-tap.txt"),
                "Android smoke must locate the real research endpoint control from the Android UI tree");
        check(smoke.contains("jarvis-research-save-tap.txt"),
                "Android smoke must locate the real research save control from the Android UI tree");

        System.out.println("AndroidResearchSettingsPersistenceEmulatorContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
