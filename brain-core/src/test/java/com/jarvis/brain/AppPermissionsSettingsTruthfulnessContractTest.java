package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards the user-facing App Permissions row against claiming access without checking Android runtime state. */
public final class AppPermissionsSettingsTruthfulnessContractTest {
    public static void main(String[] args) throws Exception {
        String settings = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));

        check(settings.contains("Manifest.permission.RECORD_AUDIO")
                        && settings.contains("Manifest.permission.READ_CONTACTS")
                        && settings.contains("Manifest.permission.READ_CALENDAR"),
                "App Permissions summary must inspect the runtime permissions behind microphone, contacts, and calendar access");
        check(settings.contains("checkSelfPermission"),
                "App Permissions summary must derive its state from Android rather than a static capability list");
        check(settings.contains("granted") && settings.contains("notification & screen access managed separately"),
                "App Permissions must distinguish runtime-granted permissions from Android-managed notification/accessibility access");
        check(!settings.contains("private String permissionSummary(){return \"Microphone, contacts, calendar, notifications and screen control\";}"),
                "App Permissions row must not remain a static list that can look like granted access");

        System.out.println("AppPermissionsSettingsTruthfulnessContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
