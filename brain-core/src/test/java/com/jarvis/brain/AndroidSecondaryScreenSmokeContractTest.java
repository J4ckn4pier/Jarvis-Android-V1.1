package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android 16 smoke coverage must open the user-facing Help and Notes screens, not merely compile them. */
public final class AndroidSecondaryScreenSmokeContractTest {
    public static void main(String[] args) throws Exception {
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));

        check(smoke.contains("com.jarvis.mobile/.CommandsActivity"),
                "Android smoke must launch Help & Features on-device");
        check(smoke.contains("JARVIS COMMANDS"),
                "Android smoke must verify distinctive Help content rendered");
        check(smoke.contains("com.jarvis.mobile/.NotesActivity"),
                "Android smoke must launch Notes & Memory on-device");
        check(smoke.contains("ADD NOTE"),
                "Android smoke must verify distinctive Notes content rendered");
        check(smoke.contains("uiautomator dump"),
                "secondary-screen smoke verification must inspect the actual Android UI tree");

        System.out.println("AndroidSecondaryScreenSmokeContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
