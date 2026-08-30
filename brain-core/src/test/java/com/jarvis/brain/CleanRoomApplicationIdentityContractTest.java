package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Charles explicitly superseded the donor package identity; the product must ship under original ownership. */
public final class CleanRoomApplicationIdentityContractTest {
    public static void main(String[] args) throws Exception {
        String gradle = Files.readString(Path.of("../android/app/build.gradle"));
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));
        String workflow = Files.readString(Path.of("../.github/workflows/build-apk.yml"));
        String activity = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));

        check(gradle.contains("applicationId 'com.jarvis.mobile'"), "applicationId must be original JARVIS identity");
        check(!gradle.contains("com.itsmylab.jarvis"), "Gradle must not retain donor application ID");
        check(!gradle.toLowerCase().contains("transplant"), "version identity must not describe a donor transplant");
        check(!smoke.contains("com.itsmylab.jarvis"), "emulator smoke must target original package");
        check(!workflow.contains("package: name='com.itsmylab.jarvis'"), "APK verification must reject donor package identity");
        check(!activity.contains("\"com.itsmylab.jarvis\".equals(getPackageName())"), "self-test must not require donor package identity");

        System.out.println("CleanRoomApplicationIdentityContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
