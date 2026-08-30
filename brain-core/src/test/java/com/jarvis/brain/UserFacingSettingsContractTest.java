package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UserFacingSettingsContractTest {
    public static void main(String[] args) throws Exception {
        Path mobile = Path.of("../android/app/src/main/java/com/jarvis/mobile");
        String settings = Files.readString(mobile.resolve("SettingsActivity.java"));
        String manifest = Files.readString(Path.of("../android/app/src/main/AndroidManifest.xml"));
        for (String title : new String[]{"Voice", "Wake Word", "Voice Model", "Language", "App Permissions", "AI Providers", "Backup & Sync", "Profile", "Default Apps", "Personality", "Widgets & Lock Screen"}) {
            check(settings.contains(title), "user Settings must include canonical group: " + title);
        }
        check(!settings.contains("PREFRONTAL CORTEX"), "normal Settings must not expose internal cortex jargon");
        check(!settings.contains("API key"), "normal Settings must not expose a raw API-key field");
        check(!settings.contains("RESEARCH ENDPOINT"), "normal Settings must not expose raw research endpoint controls");
        check(!settings.contains("127.0.0.1"), "normal Settings must not expose backend endpoint examples");
        check(Files.exists(mobile.resolve("DeveloperSettingsActivity.java")), "raw provider configuration must be preserved behind an advanced screen");
        check(manifest.contains(".DeveloperSettingsActivity"), "developer settings must remain declared rather than deleted");
        check(manifest.contains(".SettingsActivity") && manifest.contains("@style/AppTheme"), "user Settings must use the canonical dark JARVIS theme");
        System.out.println("UserFacingSettingsContractTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
