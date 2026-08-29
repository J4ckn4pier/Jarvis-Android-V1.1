package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** media_play(query) must execute a real Android search/play intent, not a string command that falls through. */
public final class AndroidMediaToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        String router = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidActionRouter.java"));

        check(factory.contains("args -> actions.playMediaQuery(args.get(\"query\"))"),
                "media_play must bind directly to the typed Android media-query action");
        check(!factory.contains("actions.execute(\"play \" + args.get(\"query\"))"),
                "media_play must not route arbitrary queries through the legacy string parser");
        check(router.contains("public String playMediaQuery(String query)"),
                "Android action layer must expose a typed media-query entry point");
        check(router.contains("MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH"),
                "media query must use Android's play-from-search intent contract");
        check(router.contains("SearchManager.QUERY, clean"),
                "media query must preserve the requested search text as structured intent data");
        check(router.contains("Tell me what you want me to play."),
                "blank media queries must fail closed instead of claiming playback");

        System.out.println("AndroidMediaToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
