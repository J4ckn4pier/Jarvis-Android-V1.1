package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** A saved Profile name must change the actual Android home greeting, not only the Settings row. */
public final class AndroidProfileRuntimeContractTest {
    public static void main(String[] args) throws Exception {
        String settings = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));
        String main = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));

        check(settings.contains("profile_name"), "Settings must persist the JARVIS profile name");
        check(main.contains("profile_name"), "MainActivity must consume the saved profile name");
        check(!main.contains("status = hudText(\"Welcome Sir!\""), "home greeting must not be hard-coded to Sir");
        check(main.contains("profileGreeting()"), "home shell must render its greeting through the persisted profile boundary");

        System.out.println("AndroidProfileRuntimeContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
