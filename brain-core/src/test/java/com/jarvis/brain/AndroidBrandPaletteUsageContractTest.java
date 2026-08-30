package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Primary Android assistant surfaces must consume the canonical donor-free JARVIS brand palette. */
public final class AndroidBrandPaletteUsageContractTest {
    public static void main(String[] args) throws Exception {
        String activity = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        String voice = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java"));
        String colors = Files.readString(Path.of("../android/app/src/main/res/values/jarvis_brand_colors.xml"));

        check(colors.contains("name=\"jarvis_bg\"")
                        && colors.contains("name=\"jarvis_bg_panel\"")
                        && colors.contains("name=\"jarvis_cyan\""),
                "canonical donor-free Android brand tokens must remain available");
        check(activity.contains("R.color.jarvis_bg")
                        && activity.contains("R.color.jarvis_bg_panel")
                        && activity.contains("R.color.jarvis_cyan"),
                "full JARVIS surface must consume the canonical brand palette instead of maintaining parallel hardcoded identity colors");
        check(voice.contains("R.color.jarvis_bg")
                        && voice.contains("R.color.jarvis_bg_panel")
                        && voice.contains("R.color.jarvis_cyan"),
                "voice overlay must consume the same canonical brand palette as the full app");

        System.out.println("AndroidBrandPaletteUsageContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
