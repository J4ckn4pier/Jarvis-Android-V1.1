package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android production reminders must remain typed and open a real user-confirmed calendar action. */
public final class AndroidReminderToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String reminder = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidReminderActions.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));

        check(reminder.contains("Intent.ACTION_INSERT"), "typed Android reminder adapter must open a real calendar insert surface");
        check(reminder.contains("CalendarContract.Events.CONTENT_URI"), "reminder insert must target Android CalendarContract.Events");
        check(reminder.contains("prepareReminder(String request)"), "reminder adapter must accept the structured request directly");
        check(factory.contains("AndroidReminderActions reminders = new AndroidReminderActions(appContext)"),
                "shared Android registry must own the typed reminder adapter");
        check(factory.contains("\"create_reminder\""), "Android production registry must override create_reminder");
        check(factory.contains("args -> reminders.prepareReminder(args.get(\"request\"))"),
                "create_reminder must preserve the structured request through the typed Android adapter");
        check(!factory.contains("actions.execute(\"schedule \" + args.get(\"request\"))"),
                "create_reminder must not flatten structured reminder data back into the legacy command parser");
        check(!factory.contains("reminder-ready"), "Android reminder binding must never report synthetic reminder-ready success");
        System.out.println("AndroidReminderToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
