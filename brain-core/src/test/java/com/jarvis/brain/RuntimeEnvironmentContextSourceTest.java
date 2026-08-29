package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/** Reasoning cortex should receive current time plus only device state relevant to the utterance. */
public final class RuntimeEnvironmentContextSourceTest {
    private static int checks;

    public static void main(String[] args) {
        DeviceStateStore devices = new DeviceStateStore();
        devices.upsert(new DeviceState("bedroom-lamp", "Bedroom Lamp", "light", true, Map.of("brightness", "35")));
        devices.upsert(new DeviceState("garage-door", "Garage Door", "door", false, Map.of("position", "closed")));
        Clock clock = Clock.fixed(Instant.parse("2026-08-29T21:10:00Z"), ZoneOffset.UTC);
        RuntimeEnvironmentContextSource source = new RuntimeEnvironmentContextSource(clock, devices);

        String lamp = source.contextFor("turn down the bedroom lamp");
        check(lamp.contains("2026-08-29T21:10:00Z"), "current time reaches reasoning context");
        check(lamp.contains("Bedroom Lamp"), "mentioned device reaches reasoning context");
        check(lamp.contains("brightness=35"), "relevant normalized device attributes reach reasoning context");
        check(!lamp.contains("Garage Door"), "unrelated device does not pollute reasoning context");

        String general = source.contextFor("what should I do next?");
        check(general.contains("2026-08-29T21:10:00Z"), "time remains available for open-ended reasoning");
        check(!general.contains("Bedroom Lamp") && !general.contains("Garage Door"),
                "device state is relevance-filtered for unrelated requests");

        System.out.println("RuntimeEnvironmentContextSourceTest: " + checks + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
