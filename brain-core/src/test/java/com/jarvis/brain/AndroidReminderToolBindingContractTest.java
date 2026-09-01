package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android production reminders must preserve structured reminder time into the real calendar insert surface. */
public final class AndroidReminderToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String reminder = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidReminderActions.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));

        check(registry.contains("Set.of(\"title\", \"start_millis\")"),
                "create_reminder must require a resolved title and start_millis instead of one free-form request");
        check(registry.contains("Open a timed personal reminder draft for user confirmation"),
                "shared tool description must tell reasoning providers that reminder time is structured before Android confirmation");
        check(reminder.contains("Intent.ACTION_INSERT"), "typed Android reminder adapter must open a real calendar insert surface");
        check(reminder.contains("CalendarContract.Events.CONTENT_URI"), "reminder insert must target Android CalendarContract.Events");
        check(reminder.contains("prepareReminder(String title, String startMillisText)"),
                "reminder adapter must accept structured title and resolved start time");
        check(reminder.contains("Long.parseLong(startMillisText"),
                "reminder adapter must validate the resolved start timestamp before opening Android");
        check(reminder.contains("CalendarContract.EXTRA_EVENT_BEGIN_TIME, start"),
                "reminder insert must prefill the actual requested time instead of putting time words only in the title");
        check(reminder.contains("CalendarContract.EXTRA_EVENT_END_TIME"),
                "reminder insert must provide a valid end time so calendar apps receive a complete timed event draft");
        check(reminder.contains("Reminder details are ready for your confirmation."),
                "Android reminder adapter must acknowledge the draft/confirmation boundary to the user");
        check(factory.contains("AndroidReminderActions reminders = new AndroidReminderActions(appContext)"),
                "shared Android registry must own the typed reminder adapter");
        check(factory.contains("\"create_reminder\""), "Android production registry must override create_reminder");
        check(factory.contains("Set.of(\"title\", \"start_millis\")"),
                "Android create_reminder override must enforce the same structured schema as the shared registry");
        check(factory.contains("args -> reminders.prepareReminder(args.get(\"title\"), args.get(\"start_millis\"))"),
                "create_reminder must preserve structured reminder title/time through the typed Android adapter");
        check(!factory.contains("actions.execute(\"schedule \" + args.get(\"request\"))"),
                "create_reminder must not flatten structured reminder data back into the legacy command parser");
        check(!factory.contains("reminder-ready"), "Android reminder binding must never report synthetic reminder-ready success");
        System.out.println("AndroidReminderToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
