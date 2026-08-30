package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Calendar-event creation must preserve structured details and leave final save/invitations to Android's editor. */
public final class AndroidCalendarEventCompositionContractTest {
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path actionPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidCalendarEventActions.java");
        check(registry.contains("r.register(spec(\"compose_calendar_event\""), "shared brain registry must expose compose_calendar_event");
        check(registry.contains("Set.of(\"title\", \"start_millis\", \"end_millis\")"), "calendar event must require explicit title/start/end");
        check(Files.exists(actionPath), "Android production must provide a typed calendar-event adapter");
        String action = Files.readString(actionPath);
        check(factory.contains("args -> calendarEvents.prepare("), "Android registry must bind structured calendar-event arguments");
        check(action.contains("Intent.ACTION_INSERT") && action.contains("CalendarContract.Events.CONTENT_URI"), "calendar event must open Android's event editor");
        check(action.contains("CalendarContract.EXTRA_EVENT_BEGIN_TIME") && action.contains("CalendarContract.EXTRA_EVENT_END_TIME"), "calendar event must preserve explicit start/end times");
        check(action.contains("CalendarContract.Events.TITLE") && action.contains("CalendarContract.Events.EVENT_LOCATION"), "calendar event must preserve title and location");
        check(action.contains("Intent.EXTRA_EMAIL"), "calendar event must preserve optional attendee addresses for Android invitation review");
        check(action.contains("Event details are ready for your confirmation."), "tool must truthfully report editor preparation, not claim an event was saved");
        check(action.contains("end <= start"), "invalid event ranges must fail closed");
        System.out.println("AndroidCalendarEventCompositionContractTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
