package com.jarvis.brain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Proves normalized device state survives restart without weakening truthful in-process behavior. */
public final class DeviceStateStorePersistenceTest {
    public static void main(String[] args) {
        MapPersistence persistence = new MapPersistence();
        DeviceState initial = new DeviceState(
                "office-lamp",
                "Office Lamp",
                "light",
                true,
                Map.of("brightness", "62", "room", "office"));

        DeviceStateStore first = new DeviceStateStore(persistence);
        first.upsert(initial);
        DeviceStateStore afterRestart = new DeviceStateStore(persistence);
        DeviceState restored = afterRestart.get("office-lamp").orElseThrow();
        check(restored.name().equals("Office Lamp"), "device name must survive restart");
        check(restored.type().equals("light"), "device type must survive restart");
        check(restored.on(), "power state must survive restart");
        check(restored.attributes().get("brightness").equals("62"), "device attributes must survive restart");

        afterRestart.setPower("office-lamp", false);
        check(!new DeviceStateStore(persistence).get("office-lamp").orElseThrow().on(),
                "power changes must persist");
        afterRestart.setAttribute("office-lamp", "brightness", "25");
        check(new DeviceStateStore(persistence).get("office-lamp").orElseThrow()
                        .attributes().get("brightness").equals("25"),
                "attribute changes must persist");
        check(afterRestart.remove("office-lamp"), "existing device must remove");
        check(new DeviceStateStore(persistence).get("office-lamp").isEmpty(),
                "removed device must not resurrect after restart");

        DeviceStateStorePersistence broken = new DeviceStateStorePersistence() {
            @Override public Map<String,DeviceState> load() { throw new IllegalStateException("unavailable"); }
            @Override public void put(DeviceState state) { throw new IllegalStateException("unavailable"); }
            @Override public void remove(String id) { throw new IllegalStateException("unavailable"); }
        };
        DeviceStateStore resilient = new DeviceStateStore(broken);
        resilient.upsert(initial);
        check(resilient.get("office-lamp").isPresent(),
                "persistence failure must not erase truthful in-process device state");
        resilient.setPower("office-lamp", false);
        check(!resilient.get("office-lamp").orElseThrow().on(),
                "persistence failure must not block in-process power changes");
        resilient.remove("office-lamp");
        check(resilient.get("office-lamp").isEmpty(),
                "persistence failure must not block in-process removal");

        System.out.println("DeviceStateStorePersistenceTest passed");
    }

    private static final class MapPersistence implements DeviceStateStorePersistence {
        private final Map<String,DeviceState> values = new LinkedHashMap<>();
        @Override public Map<String,DeviceState> load() { return Map.copyOf(values); }
        @Override public void put(DeviceState state) { values.put(state.id(), state); }
        @Override public void remove(String id) { values.remove(id); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
