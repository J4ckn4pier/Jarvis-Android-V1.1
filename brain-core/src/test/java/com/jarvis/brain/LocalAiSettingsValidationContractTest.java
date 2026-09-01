package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Protects the user-facing Free Local AI form from dismissing itself on validation errors. */
public final class LocalAiSettingsValidationContractTest {
    public static void main(String[] args) throws Exception {
        Path mobile = Path.of("../android/app/src/main/java/com/jarvis/mobile");
        String settings = Files.readString(mobile.resolve("SettingsActivity.java"));
        String localAiPolicy = Files.readString(mobile.resolve("brain/providers/LocalAiEndpointPolicy.java"));
        int start = settings.indexOf("private void showLocalAiSetup()");
        int end = settings.indexOf("private void disconnectProvider()", start);
        check(start >= 0 && end > start, "Settings must retain a dedicated Free Local AI setup method");
        String localAiSetup = settings.substring(start, end);

        // Validation failures must be correctable in-place rather than closing the form.
        check(localAiSetup.contains("setPositiveButton(\"SAVE\",null)"),
                "Free Local AI SAVE must not use AlertDialog's auto-dismiss click listener");
        check(localAiSetup.contains("setOnShowListener"),
                "Free Local AI must attach validation after the dialog is shown so invalid input can remain editable");
        check(localAiSetup.contains("getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener"),
                "Free Local AI must own SAVE click handling and keep the form open when validation fails");
        check(localAiSetup.contains("dialog.dismiss()"),
                "Free Local AI must dismiss explicitly only after a valid configuration is persisted");
        check(localAiSetup.contains("LocalAiEndpointPolicy.allows(endpointValue)"),
                "Free Local AI must continue enforcing the safe local endpoint policy before persistence");
        check(localAiPolicy.contains("\"10.0.2.2\".equals(host)"),
                "Free Local AI must accept Android emulator host alias 10.0.2.2 because the shared safe transport policy already treats it as the local development host");

        System.out.println("LocalAiSettingsValidationContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
