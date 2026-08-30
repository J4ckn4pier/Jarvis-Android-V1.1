package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Pins legacy raw-command routing outside the production structured tool registry. */
public final class LegacyAndroidActionRouterNamedTargetSafetyContractTest {
    public static void main(String[] args) throws Exception {
        Path sourceRoot = Path.of("../android/app/src/main/java");
        Path routerPath = sourceRoot.resolve("com/jarvis/mobile/actions/AndroidActionRouter.java");
        Path factoryPath = sourceRoot.resolve("com/jarvis/mobile/brain/AndroidToolRegistryFactory.java");
        check(Files.exists(routerPath), "legacy Android action router must remain identifiable while compatibility commands exist");
        check(Files.exists(factoryPath), "production Android tool registry must exist");
        String router = Files.readString(routerPath);
        String factory = Files.readString(factoryPath);

        check(router.contains("private String contactValue(String name, Uri uri, String valueColumn)"),
                "legacy contact lookup must remain explicitly identifiable until it is hardened or removed");
        check(!factory.contains("AndroidActionRouter"),
                "production brain tool registry must not route consequential actions through the legacy raw-command router");
        check(factory.contains("AndroidMessagingActions messaging = new AndroidMessagingActions(appContext)"),
                "production messaging must use the hardened typed adapter");
        check(factory.contains("AndroidEmailActions email = new AndroidEmailActions(appContext)"),
                "production email must use the hardened typed adapter");
        check(factory.contains("AndroidDialerActions dialer = new AndroidDialerActions(appContext)"),
                "production dialer opening must use the typed adapter");

        List<Path> productionReferences;
        try (var files = Files.walk(sourceRoot)) {
            productionReferences = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.equals(routerPath))
                    .filter(path -> contains(path, "AndroidActionRouter"))
                    .toList();
        }
        check(productionReferences.isEmpty(),
                "legacy AndroidActionRouter must stay unreachable from production Java; references=" + productionReferences);

        System.out.println("LegacyAndroidActionRouterNamedTargetSafetyContractTest passed");
    }

    private static boolean contains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
