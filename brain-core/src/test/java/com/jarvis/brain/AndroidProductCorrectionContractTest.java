package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins corrections that are actually implemented after the failed Samsung release candidate. */
public final class AndroidProductCorrectionContractTest {
    public static void main(String[] args) throws Exception {
        String settings = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceInteractionService.java"));
        String detector = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/AndroidWakeWordDetectorFactory.java"));
        String onboarding = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/AssistantRoleOnboardingActivity.java"));
        String manifest = Files.readString(Path.of("../android/app/src/main/AndroidManifest.xml"));
        String gradle = Files.readString(Path.of("../android/app/build.gradle"));

        check(gradle.contains("assets/ui/claude-artifact"), "Android build must package the canonical Claude artifact asset directory");
        check(settings.contains("showProfileEditor"), "Profile row must edit persisted profile data");
        check(settings.contains("showPersonalityPicker"), "Personality row must persist a real selection");
        check(settings.contains("showBackupSyncSettings"), "Backup & Sync row must expose real persisted controls");
        check(settings.contains("showWidgetLockSettings"), "Widgets & Lock Screen row must expose real persisted controls");
        check(settings.contains("showVoiceModelPicker"), "Voice Model row must provide a real selectable setting");
        check(settings.contains("if (checked && !isAssistantRoleHeld()) requestAssistant()"), "enabling passive wake must surface the required Android assistant-role setup");
        check(service.contains("armPassiveWake(\"service ready\")"), "system-bound voice service must still arm passive wake when Android binds it");
        check(!detector.contains("return null;"), "production wake detector factory must not contain a null custom-detector dead end");
        check(manifest.contains(".AssistantRoleOnboardingActivity") && manifest.contains("android.intent.category.LAUNCHER"), "normal app launch must pass through explicit assistant-role onboarding");
        check(onboarding.contains("ROLE_ASSISTANT") && onboarding.contains("Wake word requires JARVIS as your Android assistant"), "onboarding must explain and request the Android assistant role required by passive wake");
        check(onboarding.contains("Manifest.permission.RECORD_AUDIO") && onboarding.contains("requestPermissions"), "wake onboarding must request microphone permission before binding the passive-wake assistant role");
        check(onboarding.contains("JarvisVoiceInteractionService.refreshPassiveWakePreference()"), "permission/role completion must explicitly re-arm passive wake");
        check(onboarding.contains("new Intent(this, MainActivity.class)"), "onboarding must continue into the real JARVIS app after setup or deferral");

        System.out.println("AndroidProductCorrectionContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
