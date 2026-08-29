package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Captured notification contents may enter reasoning only through an explicit relevance gate. */
public final class AndroidNotificationReasoningContextContractTest {
    public static void main(String[] args) throws Exception {
        Path sourcePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidRecentNotificationContextSource.java");
        String runtime = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java"));

        check(Files.exists(sourcePath), "Android must expose captured notifications through a dedicated context source");
        String source = Files.readString(sourcePath);
        check(source.contains("JarvisDatabase.get(context).recentNotifications"),
                "notification context must read the existing private captured-notification store");
        check(runtime.contains("new KeywordGatedAssistantContextSource"),
                "notification context must be relevance-gated before it can be read");
        check(runtime.contains("new AndroidRecentNotificationContextSource(app)"),
                "Android runtime must attach the dedicated notification context source");
        check(runtime.contains("\"notification\"") && runtime.contains("\"what did i miss\""),
                "runtime gate must require explicit notification relevance phrases");

        System.out.println("AndroidNotificationReasoningContextContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
