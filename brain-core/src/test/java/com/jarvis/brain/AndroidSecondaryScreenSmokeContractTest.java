package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android 16 smoke coverage must open the user-facing Help and Notes screens, not merely compile them. */
public final class AndroidSecondaryScreenSmokeContractTest {
    public static void main(String[] args) throws Exception {
        String smoke = Files.readString(Path.of("../.github/scripts/emulator-smoke.sh"));
        String debugManifest = Files.readString(Path.of("../android/app/src/debug/AndroidManifest.xml"));
        String productionManifest = Files.readString(Path.of("../android/app/src/main/AndroidManifest.xml"));

        check(smoke.contains("$PACKAGE/.CommandsActivity"),
                "Android smoke must launch Help & Features on-device through the production package variable");
        check(smoke.contains("JARVIS COMMANDS"),
                "Android smoke must verify distinctive Help content rendered");
        check(smoke.contains("$PACKAGE/.NotesActivity"),
                "Android smoke must launch Notes & Memory on-device through the production package variable");
        check(smoke.contains("ADD NOTE"),
                "Android smoke must verify distinctive Notes content rendered");
        check(smoke.contains("uiautomator dump"),
                "secondary-screen smoke verification must inspect the actual Android UI tree");

        checkExported(debugManifest, "CommandsActivity",
                "debug manifest must export Help for connected emulator smoke tests");
        checkExported(debugManifest, "NotesActivity",
                "debug manifest must export Notes for connected emulator smoke tests");
        checkPrivate(productionManifest, "CommandsActivity",
                "production Help activity must remain private");
        checkPrivate(productionManifest, "NotesActivity",
                "production Notes activity must remain private");

        System.out.println("AndroidSecondaryScreenSmokeContractTest: PASS");
    }

    private static void checkExported(String manifest, String className, String message) {
        check(activityDeclaration(manifest, className).contains("android:exported=\"true\""), message);
    }

    private static void checkPrivate(String manifest, String className, String message) {
        check(!activityDeclaration(manifest, className).contains("android:exported=\"true\""), message);
    }

    private static String activityDeclaration(String manifest, String className) {
        String shortMarker = "android:name=\"." + className + "\"";
        String qualifiedMarker = "android:name=\"com.jarvis.mobile." + className + "\"";
        int start = manifest.indexOf(shortMarker);
        if (start < 0) start = manifest.indexOf(qualifiedMarker);
        check(start >= 0, "manifest missing " + className);
        int end = manifest.indexOf('>', start);
        check(end > start, "manifest has malformed declaration for " + className);
        return manifest.substring(start, end);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
