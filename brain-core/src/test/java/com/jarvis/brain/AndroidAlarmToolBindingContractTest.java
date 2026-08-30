package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** set_alarm(hour, minute) must remain typed through Android and fail closed on invalid clock values. */
public final class AndroidAlarmToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path alarmPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidAlarmActions.java");

        check(registry.contains("r.register(spec(\"set_alarm\""),
                "shared brain registry must expose a typed set_alarm tool");
        check(registry.contains("Set.of(\"hour\", \"minute\")"),
                "set_alarm must require structured hour and minute arguments");
        check(Files.exists(alarmPath), "Android production must provide a typed alarm adapter");
        String alarm = Files.readString(alarmPath);
        check(factory.contains("AndroidAlarmActions alarm = new AndroidAlarmActions(appContext)"),
                "Android tool registry must compose the typed alarm adapter");
        check(factory.contains("args -> alarm.setAlarm(args.get(\"hour\"), args.get(\"minute\"))"),
                "set_alarm must preserve structured hour/minute arguments into Android");
        check(alarm.contains("AlarmClock.ACTION_SET_ALARM"),
                "typed alarm action must use Android's ACTION_SET_ALARM capability");
        check(alarm.contains("AlarmClock.EXTRA_HOUR") && alarm.contains("AlarmClock.EXTRA_MINUTES"),
                "typed alarm action must pass clock values through Android extras");
        check(alarm.contains("hour < 0 || hour > 23") && alarm.contains("minute < 0 || minute > 59"),
                "typed alarm action must reject invalid local clock values");
        check(alarm.contains("resolveActivity(context.getPackageManager()) == null"),
                "typed alarm action must fail closed when no alarm app resolves the intent");

        System.out.println("AndroidAlarmToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
