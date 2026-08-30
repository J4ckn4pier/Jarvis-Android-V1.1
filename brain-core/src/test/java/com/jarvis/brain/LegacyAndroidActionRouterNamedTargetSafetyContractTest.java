package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Pins the retired raw-command brain/router pair outside every active production composition root. */
public final class LegacyAndroidActionRouterNamedTargetSafetyContractTest {
    public static void main(String[] args) throws Exception {
        Path sourceRoot = Path.of("../android/app/src/main/java");
        Path routerPath = sourceRoot.resolve("com/jarvis/mobile/actions/AndroidActionRouter.java");
        Path retiredBrainPath = sourceRoot.resolve("com/jarvis/mobile/brain/JarvisBrain.java");
        Path factoryPath = sourceRoot.resolve("com/jarvis/mobile/brain/AndroidToolRegistryFactory.java");
        check(Files.exists(routerPath), "legacy Android action router must remain identifiable while compatibility code exists");
        check(Files.exists(retiredBrainPath), "retired JarvisBrain compatibility class must remain identifiable until removed");
        check(Files.exists(factoryPath), "production Android tool registry must exist");
        String router = Files.readString(routerPath);
        String retiredBrain = Files.readString(retiredBrainPath);
        String factory = Files.readString(factoryPath);

        check(router.contains("private String contactValue(String name, Uri uri, String valueColumn)"),
                "legacy contact lookup must remain explicitly identifiable until the retired router is removed");
        check(retiredBrain.contains("new AndroidActionRouter(context)"),
                "retired JarvisBrain must remain the only quarantined owner of the raw-command router until both are removed together");
        check(!factory.contains("AndroidActionRouter") && !factory.contains("JarvisBrain"),
                "production tool composition must not route through retired raw-command brain code");
        check(factory.contains("AndroidMessagingActions messaging = new AndroidMessagingActions(appContext)"),
                "production messaging must use the hardened typed adapter");
        check(factory.contains("AndroidEmailActions email = new AndroidEmailActions(appContext)"),
                "production email must use the hardened typed adapter");
        check(factory.contains("AndroidDialerActions dialer = new AndroidDialerActions(appContext)"),
                "production dialer opening must use the typed adapter");

        List<Path> routerReferences;
        List<Path> retiredBrainReferences;
        try (var files = Files.walk(sourceRoot)) {
            List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
            routerReferences = javaFiles.stream()
                    .filter(path -> !path.equals(routerPath) && !path.equals(retiredBrainPath))
                    .filter(path -> contains(path, "AndroidActionRouter"))
                    .toList();
            retiredBrainReferences = javaFiles.stream()
                    .filter(path -> !path.equals(retiredBrainPath))
                    .filter(path -> contains(path, "JarvisBrain"))
                    .toList();
        }
        check(routerReferences.isEmpty(),
                "legacy AndroidActionRouter may only be referenced by the quarantined retired JarvisBrain; references=" + routerReferences);
        check(retiredBrainReferences.isEmpty(),
                "retired JarvisBrain must stay unreachable from all active production Java; references=" + retiredBrainReferences);

        System.out.println("LegacyAndroidActionRouterNamedTargetSafetyContractTest passed");
    }

    private static boolean contains(Path path, String token) {
        try { return Files.readString(path).contains(token); }
        catch (Exception ignored) { return false; }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
