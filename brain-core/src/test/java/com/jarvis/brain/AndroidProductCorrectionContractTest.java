package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins the user-visible corrections required after the failed Samsung release candidate. */
public final class AndroidProductCorrectionContractTest {
    public static void main(String[] args) throws Exception {
        String main = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        String settings = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));
        String detector = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidWakeWordDetectorFactory.java"));
        String gradle = Files.readString(Path.of("../android/app/build.gradle"));

        check(!main.contains("buildCurrentShell()"),
                "failed handmade HUD must not remain the production entry surface");
        check(main.contains("ensureAssistantRoleOnboarding"),
                "first-run product path must explicitly surface Android assistant-role onboarding");
        check(main.contains("CanonicalJarvisSurface"),
                "main activity must render through the canonical JARVIS surface bridge");
        check(gradle.contains("assets/ui/claude-artifact"),
                "Android build must package the canonical Claude artifact asset directory");

        check(settings.contains("showProfileEditor"), "Profile row must edit persisted profile data");
        check(settings.contains("showPersonalityPicker"), "Personality row must persist a real selection");
        check(settings.contains("showBackupSyncSettings"), "Backup & Sync row must expose real persisted controls");
        check(settings.contains("showWidgetLockSettings"), "Widgets & Lock Screen row must expose real persisted controls");
        check(settings.contains("showVoiceModelPicker"), "Voice Model row must provide a real selectable setting");

        check(service.contains("armPassiveWake(\"service ready\")"),
                "system-bound voice service must still arm passive wake when Android binds it");
        check(!detector.contains("return null;"),
                "production wake detector factory must not contain a null custom-detector dead end");

        System.out.println("AndroidProductCorrectionContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
