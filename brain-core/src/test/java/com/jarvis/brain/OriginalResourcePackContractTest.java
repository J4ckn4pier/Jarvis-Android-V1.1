package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locks Claude's approved clean-room launcher/status/palette resource handoff into source and APK verification. */
public final class OriginalResourcePackContractTest {
    public static void main(String[] args) throws Exception {
        Path res = Path.of("../android/app/src/main/res");
        Path background = res.resolve("drawable/ic_launcher_background.xml");
        Path foreground = res.resolve("drawable/ic_launcher_foreground.xml");
        Path status = res.resolve("drawable/ic_stat_jarvis.xml");
        Path colors = res.resolve("values/jarvis_brand_colors.xml");
        Path launcher = res.resolve("mipmap-anydpi-v26/ic_launcher.xml");
        Path launcherRound = res.resolve("mipmap-anydpi-v26/ic_launcher_round.xml");
        Path legacyLauncher = res.resolve("mipmap-xxxhdpi/ic_launcher.png");
        String workflow = Files.readString(Path.of("../.github/workflows/build-apk.yml"));

        check(Files.exists(background), "original launcher background resource missing");
        check(Files.exists(foreground), "original launcher foreground resource missing");
        check(Files.exists(status), "original notification icon resource missing");
        check(Files.exists(colors), "original brand palette missing");
        check(Files.exists(launcher), "adaptive launcher resource missing");
        check(Files.exists(launcherRound), "adaptive round launcher resource missing");
        check(!Files.exists(legacyLauncher), "legacy raster launcher must be removed after original replacement lands");

        String bg = Files.readString(background);
        String fg = Files.readString(foreground);
        String palette = Files.readString(colors);
        String adaptive = Files.readString(launcher);
        check(bg.contains("#101A28") && bg.contains("#060910"), "launcher background must use approved original gradient");
        check(fg.contains("#FFBDF1FF") && fg.contains("#FF55D6FF") && fg.contains("#FF082433"), "launcher foreground must use approved original orb gradient");
        check(palette.contains("name=\"jarvis_cyan\">#55D6FF"), "brand palette must expose approved cyan token");
        check(adaptive.contains("@drawable/ic_launcher_background") && adaptive.contains("@drawable/ic_launcher_foreground"), "adaptive launcher must compose approved original layers");

        check(workflow.contains("aapt dump resources \"$APK\""), "APK verification must inspect compiled resource table");
        check(workflow.contains("ic_launcher_background"), "APK verification must require original launcher background");
        check(workflow.contains("ic_launcher_foreground"), "APK verification must require original launcher foreground");
        check(workflow.contains("ic_stat_jarvis"), "APK verification must require original notification icon");
        System.out.println("OriginalResourcePackContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
