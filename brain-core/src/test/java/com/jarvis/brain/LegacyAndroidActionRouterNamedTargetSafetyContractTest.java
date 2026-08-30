package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Prevents the retired raw-command brain/router from returning to the production Android package. */
public final class LegacyAndroidActionRouterNamedTargetSafetyContractTest {
    public static void main(String[] args) throws Exception {
        Path sourceRoot = Path.of("../android/app/src/main/java");
        Path routerPath = sourceRoot.resolve("com/jarvis/mobile/actions/AndroidActionRouter.java");
        Path retiredBrainPath = sourceRoot.resolve("com/jarvis/mobile/brain/JarvisBrain.java");
        Path factoryPath = sourceRoot.resolve("com/jarvis/mobile/brain/AndroidToolRegistryFactory.java");

        check(!Files.exists(routerPath),
                "retired raw-command AndroidActionRouter must not ship in production sources");
        check(!Files.exists(retiredBrainPath),
                "retired JarvisBrain compatibility implementation must not ship in production sources");
        check(Files.exists(factoryPath), "production Android tool registry must exist");

        String factory = Files.readString(factoryPath);
        check(!factory.contains("AndroidActionRouter") && !factory.contains("JarvisBrain"),
                "production tool composition must not route through retired raw-command brain code");
        check(factory.contains("AndroidMessagingActions messaging = new AndroidMessagingActions(appContext)"),
                "production messaging must use the hardened typed adapter");
        check(factory.contains("AndroidEmailActions email = new AndroidEmailActions(appContext)"),
                "production email must use the hardened typed adapter");
        check(factory.contains("AndroidDialerActions dialer = new AndroidDialerActions(appContext)"),
                "production dialer must use the hardened typed adapter");

        try (var files = Files.walk(sourceRoot)) {
            var staleReferences = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "AndroidActionRouter") || contains(path, "new JarvisBrain(") || contains(path, "JarvisBrain."))
                    .toList();
            check(staleReferences.isEmpty(),
                    "retired raw-command brain/router references must not remain; references=" + staleReferences);
        }

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
