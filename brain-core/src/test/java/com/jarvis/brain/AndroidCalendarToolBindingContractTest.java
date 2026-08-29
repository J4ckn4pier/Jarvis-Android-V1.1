package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Android production calendar awareness must use the device calendar, with an explicit runtime permission boundary. */
public final class AndroidCalendarToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String manifest = Files.readString(Path.of("../android/app/src/main/AndroidManifest.xml"));
        String activity = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
        String router = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidActionRouter.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));

        check(manifest.contains("android.permission.READ_CALENDAR"), "Android manifest must request READ_CALENDAR");
        check(activity.contains("Manifest.permission.READ_CALENDAR"), "MainActivity must request calendar runtime permission");
        check(router.contains("calendarCommitments"), "Android action router must expose real calendar reads");
        check(router.contains("CalendarContract.Instances"), "calendar reads must use CalendarContract.Instances");
        check(router.contains("Manifest.permission.READ_CALENDAR"), "calendar reads must enforce READ_CALENDAR permission");
        check(factory.contains("\"calendar_query\""), "Android production registry must override calendar_query");
        check(factory.contains("calendarCommitments"), "calendar_query must call the real Android calendar reader");
        System.out.println("AndroidCalendarToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
