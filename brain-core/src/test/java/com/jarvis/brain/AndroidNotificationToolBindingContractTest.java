package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android production registry must bind notification_query to captured notification data, not a placeholder success. */
public final class AndroidNotificationToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        String database = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/memory/JarvisDatabase.java"));

        check(database.contains("recentNotifications"), "Android database must expose captured notification history");
        check(factory.contains("\"notification_query\""), "Android production registry must override notification_query");
        check(factory.contains("recentNotifications"), "notification_query must read the captured Android notification history");
        check(!factory.contains("notification_query\", false, Set.of(\"notifications\"), Set.of(),\n                \"Read captured notifications\", ToolExecutionClass.DEVICE_REFLEX,\n                args -> ToolResult.success"),
                "notification_query must not be a synthetic success");
        System.out.println("AndroidNotificationToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
