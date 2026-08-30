package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** set_timer(amount, unit) must preserve structured duration data into Android's timer contract. */
public final class AndroidTimerToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path timerPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidTimerActions.java");
        check(Files.exists(timerPath), "Android production must provide a typed timer action adapter");
        String timer = Files.readString(timerPath);
        String manifest = Files.readString(Path.of("../android/app/src/main/AndroidManifest.xml"));

        check(factory.contains("AndroidTimerActions timer = new AndroidTimerActions(appContext)"),
                "Android tool registry must compose the typed timer adapter");
        check(factory.contains("args -> timer.setTimer(args.get(\"amount\"), args.get(\"unit\"))"),
                "set_timer must preserve structured amount/unit arguments into the Android adapter");
        check(!factory.contains("actions.execute(\"set timer for \" + args.get(\"amount\") + \" \" + args.get(\"unit\"))"),
                "set_timer must not flatten structured duration arguments into the legacy parser");
        check(timer.contains("AlarmClock.ACTION_SET_TIMER"),
                "typed timer action must use Android's ACTION_SET_TIMER capability");
        check(timer.contains("AlarmClock.EXTRA_LENGTH"),
                "typed timer action must provide duration seconds through EXTRA_LENGTH");
        check(timer.contains("resolveActivity(context.getPackageManager()) == null"),
                "typed timer action must fail closed when no timer app resolves the intent");
        check(timer.contains("Math.multiplyExact"),
                "typed timer conversion must reject overflow rather than silently wrap");
        check(manifest.contains("android:name=\"com.android.alarm.permission.SET_ALARM\""),
                "Android manifest must request the Clock intent SET_ALARM permission used by ACTION_SET_TIMER");
        check(!manifest.contains("android:name=\"android.permission.SET_ALARM\""),
                "Android manifest must not use the non-existent android.permission.SET_ALARM permission name");

        System.out.println("AndroidTimerToolBindingContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
