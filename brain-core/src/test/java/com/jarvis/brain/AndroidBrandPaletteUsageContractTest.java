package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** User-visible Android assistant surfaces must consume the canonical donor-free JARVIS brand palette. */
public final class AndroidBrandPaletteUsageContractTest {
    public static void main(String[] args) throws Exception {
        Path mobile = Path.of("../android/app/src/main/java/com/jarvis/mobile");
        String activity = Files.readString(mobile.resolve("MainActivity.java"));
        String voice = Files.readString(mobile.resolve("assistant/JarvisVoiceSession.java"));
        String commands = Files.readString(mobile.resolve("CommandsActivity.java"));
        String notes = Files.readString(mobile.resolve("NotesActivity.java"));
        String diagnostics = Files.readString(mobile.resolve("DiagnosticsActivity.java"));
        String colors = Files.readString(Path.of("../android/app/src/main/res/values/jarvis_brand_colors.xml"));

        check(colors.contains("name=\"jarvis_bg\"")
                        && colors.contains("name=\"jarvis_bg_panel\"")
                        && colors.contains("name=\"jarvis_cyan\"")
                        && colors.contains("name=\"jarvis_text_dim\""),
                "canonical donor-free Android brand tokens must remain available");
        requirePrimarySurface(activity, "full JARVIS surface");
        requirePrimarySurface(voice, "voice overlay");
        requirePrimarySurface(commands, "Help & Features surface");
        requirePrimarySurface(notes, "Notes & Memory surface");
        requirePrimarySurface(diagnostics, "Diagnostics surface");

        System.out.println("AndroidBrandPaletteUsageContractTest: PASS");
    }

    private static void requirePrimarySurface(String source, String name) {
        check(source.contains("R.color.jarvis_bg")
                        && source.contains("R.color.jarvis_bg_panel")
                        && source.contains("R.color.jarvis_cyan"),
                name + " must consume the canonical brand palette instead of maintaining parallel hardcoded identity colors");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
