package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android production reminders must open a real user-confirmed calendar action, not report synthetic success. */
public final class AndroidReminderToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String router = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidActionRouter.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));

        check(router.contains("Intent.ACTION_INSERT"), "Android router must support a real calendar insert surface");
        check(router.contains("CalendarContract.Events.CONTENT_URI"), "calendar insert must target Android CalendarContract.Events");
        check(factory.contains("\"create_reminder\""), "Android production registry must override create_reminder");
        check(factory.contains("actions.execute(\"schedule \" + args.get(\"request\"))"),
                "create_reminder must route the requested reminder to the real Android calendar editor");
        check(!factory.contains("reminder-ready"), "Android reminder binding must never report synthetic reminder-ready success");
        System.out.println("AndroidReminderToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
