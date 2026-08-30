package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins legacy raw-command routing outside production and prevents wrong-person guesses if compatibility code is invoked. */
public final class LegacyAndroidActionRouterNamedTargetSafetyContractTest {
    public static void main(String[] args) throws Exception {
        Path routerPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidActionRouter.java");
        Path factoryPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java");
        check(Files.exists(routerPath), "legacy Android action router must remain identifiable while compatibility commands exist");
        check(Files.exists(factoryPath), "production Android tool registry must exist");
        String router = Files.readString(routerPath);
        String factory = Files.readString(factoryPath);

        check(!factory.contains("AndroidActionRouter"),
                "production brain tool registry must not route consequential actions through the legacy raw-command router");
        check(factory.contains("AndroidMessagingActions messaging = new AndroidMessagingActions(appContext)"),
                "production messaging must use the hardened typed adapter");
        check(factory.contains("AndroidEmailActions email = new AndroidEmailActions(appContext)"),
                "production email must use the hardened typed adapter");
        check(factory.contains("AndroidDialerActions dialer = new AndroidDialerActions(appContext)"),
                "production dialer opening must use the typed adapter");

        check(router.contains("UniqueNamedTargetResolver.Candidate"),
                "legacy named-contact compatibility actions must collect candidates for the shared ambiguity resolver");
        check(router.contains("UniqueNamedTargetResolver.resolve(name, candidates).orElse(null)"),
                "legacy named-contact compatibility actions must use the shared exact-unique resolver");
        check(!router.contains("displayName + \" LIKE ?\""),
                "legacy contact lookup must not select from partial LIKE matches");
        check(!router.contains("if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);"),
                "legacy contact lookup must never choose the first provider row");

        System.out.println("LegacyAndroidActionRouterNamedTargetSafetyContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
