package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Prevents the legacy raw-command router from guessing the first partial contact match. */
public final class LegacyAndroidActionRouterNamedTargetSafetyContractTest {
    public static void main(String[] args) throws Exception {
        Path routerPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidActionRouter.java");
        check(Files.exists(routerPath), "legacy Android action router must exist while compatibility commands remain supported");
        String router = Files.readString(routerPath);

        check(router.contains("UniqueNamedTargetResolver.Candidate"),
                "legacy named-contact actions must collect candidates for the shared ambiguity resolver");
        check(router.contains("UniqueNamedTargetResolver.resolve(name, candidates)"),
                "legacy named-contact actions must use the shared exact-unique resolver");
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
