package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards Widgets & Lock Screen against persisting a lock-screen capability that production does not consume. */
public final class WidgetsLockScreenSettingsTruthfulnessContractTest {
    public static void main(String[] args) throws Exception {
        String settings = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));
        String widget = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/widgets/QuickActivationWidget.java"));

        check(widget.contains("AppWidgetProvider") && widget.contains("MainActivity.class"),
                "Quick Access must remain a real Android widget that launches the production app");
        check(settings.contains("requestQuickAccessWidget"),
                "Widgets settings must keep the working Quick Access widget entry point");
        check(!settings.contains("putBoolean(\"lock_screen_assistant_enabled\"")
                        && !settings.contains("getBoolean(\"lock_screen_assistant_enabled\""),
                "Settings must not persist or report an unconsumed lock-screen assistant preference");
        check(settings.contains("Lock-screen assistant access is managed by Android")
                        || settings.contains("lock-screen access is managed by Android"),
                "Settings must truthfully explain that lock-screen assistant access is Android-managed");

        System.out.println("WidgetsLockScreenSettingsTruthfulnessContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
