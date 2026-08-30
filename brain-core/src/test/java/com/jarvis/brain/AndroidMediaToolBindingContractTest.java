package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Media requests must stay typed through Android for both search/play and transport controls. */
public final class AndroidMediaToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        String media = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidMediaActions.java"));

        check(factory.contains("args -> media.playMediaQuery(args.get(\"query\"))"),
                "media_play must bind directly to the typed Android media-query action");
        check(!factory.contains("actions.execute(\"play \" + args.get(\"query\"))"),
                "media_play must not route arbitrary queries through the legacy string parser");
        check(media.contains("public String playMediaQuery(String query)"),
                "Android action layer must expose a typed media-query entry point");
        check(media.contains("MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH"),
                "media query must use Android's play-from-search intent contract");
        check(media.contains("SearchManager.QUERY, clean"),
                "media query must preserve the requested search text as structured intent data");
        check(media.contains("Tell me what you want me to play."),
                "blank media queries must fail closed instead of claiming playback");
        check(media.contains("resolveActivity(context.getPackageManager()) == null"),
                "media query must fail closed when no compatible media app exists");

        check(registry.contains("r.register(spec(\"media_control\""),
                "shared brain registry must expose typed media transport control");
        check(registry.contains("Set.of(\"action\")"),
                "media_control must require an explicit action argument");
        check(factory.contains("args -> media.control(args.get(\"action\"))"),
                "Android production registry must bind media_control to the typed media adapter");
        check(media.contains("public String control(String action)"),
                "Android media adapter must expose typed transport controls");
        check(media.contains("KeyEvent.KEYCODE_MEDIA_PAUSE") && media.contains("KeyEvent.KEYCODE_MEDIA_PLAY")
                        && media.contains("KeyEvent.KEYCODE_MEDIA_NEXT") && media.contains("KeyEvent.KEYCODE_MEDIA_PREVIOUS"),
                "typed media controls must support pause, resume/play, next, and previous");
        check(media.contains("dispatchMediaKeyEvent"),
                "typed media transport controls must dispatch through Android AudioManager");
        check(media.contains("Unsupported media action"),
                "unknown media-control actions must fail closed rather than guessing");

        System.out.println("AndroidMediaToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
