package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins legacy raw-command routing outside the production structured tool registry. */
public final class LegacyAndroidActionRouterNamedTargetSafetyContractTest {
    public static void main(String[] args) throws Exception {
        Path routerPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidActionRouter.java");
        Path factoryPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java");
        check(Files.exists(routerPath), "legacy Android action router must remain identifiable while compatibility commands exist");
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

        System.out.println("LegacyAndroidActionRouterNamedTargetSafetyContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
