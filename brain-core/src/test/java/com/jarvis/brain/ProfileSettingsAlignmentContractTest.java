package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProfileSettingsAlignmentContractTest {
    public static void main(String[] args) throws Exception {
        Path mobile = Path.of("../android/app/src/main/java/com/jarvis/mobile");
        String settings = Files.readString(mobile.resolve("SettingsActivity.java"));
        String providerSchema = Files.readString(mobile.resolve("brain/providers/ProviderSharedPlanSchema.java"));

        check(providerSchema.contains("if (value.length() > 80)"),
                "provider boundary must retain the bounded 80-character profile-name contract");
        check(settings.contains("InputFilter.LengthFilter(80)"),
                "Profile Settings must prevent saving a value longer than the 80 characters the runtime consumes");
        check(settings.contains("replaceAll(\"[\\\\p{Cntrl}\\\\r\\\\n]+\", \" \" )")
                        || settings.contains("replaceAll(\"[\\\\p{Cntrl}\\\\r\\\\n]+\", \" \")"),
                "Profile Settings must normalize control/newline characters before persistence so the displayed value matches runtime address context");

        System.out.println("ProfileSettingsAlignmentContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
