package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Assistant-role, permission, and terminal session lifecycle completion must re-arm JARVIS passive wake. */
public final class AndroidAssistantRoleWakeRearmContractTest {
    public static void main(String[] args) throws Exception {
        String main = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        String managedSession = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/ManagedJarvisVoiceSession.java"));
        check(main.contains("requestCode == ASSISTANT_ROLE_REQUEST"), "MainActivity must handle Assistant role result");
        check(main.contains("JarvisVoiceInteractionService.refreshPassiveWakePreference()"),
                "Assistant role completion must explicitly refresh passive wake");
        check(main.contains("@Override public void onRequestPermissionsResult"),
                "MainActivity must react when runtime microphone permission is granted");
        check(main.contains("requestCode == PERMISSION_REQUEST"),
                "runtime permission completion must be tied to JARVIS permission onboarding");
        check(main.contains("checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED"),
                "passive wake must only re-arm after microphone permission is actually granted");
        check(managedSession.contains("@Override public void onDestroy()")
                        && managedSession.contains("JarvisVoiceInteractionService.rearmPassiveWakeAfterSession();"),
                "Samsung/OEM Assistant-session destruction must re-arm passive wake even when Android skips the normal onHide callback");
        System.out.println("AndroidAssistantRoleWakeRearmContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
