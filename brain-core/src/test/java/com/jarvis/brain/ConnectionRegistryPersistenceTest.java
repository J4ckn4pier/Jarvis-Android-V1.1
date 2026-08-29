package com.jarvis.brain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Proves non-secret connection/auth state survives restart without persisting credential material. */
public final class ConnectionRegistryPersistenceTest {
    public static void main(String[] args) {
        MapPersistence persistence = new MapPersistence();
        ConnectionRegistry first = new ConnectionRegistry(persistence);
        Instant connectedAt = Instant.parse("2026-08-29T16:00:00Z");
        first.register("spotify", ConnectionType.WEB_OAUTH);
        first.markConnected("spotify", connectedAt);
        first.register("local-cortex", ConnectionType.AI_PROVIDER);

        ConnectionRegistry afterRestart = new ConnectionRegistry(persistence);
        ConnectionState spotify = afterRestart.get("spotify").orElseThrow();
        check(spotify.connected(), "connected state must survive restart");
        check(connectedAt.equals(spotify.connectedAt()), "connection timestamp must survive restart");
        check(spotify.type() == ConnectionType.WEB_OAUTH, "connection type must survive restart");
        check(!afterRestart.get("local-cortex").orElseThrow().connected(),
                "registered-but-disconnected state must survive restart");

        afterRestart.disconnect("spotify");
        ConnectionRegistry afterDisconnectRestart = new ConnectionRegistry(persistence);
        check(!afterDisconnectRestart.get("spotify").orElseThrow().connected(),
                "disconnect must persist and clear connected timestamp");
        check(afterDisconnectRestart.get("spotify").orElseThrow().connectedAt() == null,
                "disconnect must not retain stale connected timestamp");

        ConnectionRegistryPersistence broken = new ConnectionRegistryPersistence() {
            @Override public Map<String,ConnectionState> load() { throw new IllegalStateException("unavailable"); }
            @Override public void put(ConnectionState state) { throw new IllegalStateException("unavailable"); }
        };
        ConnectionRegistry resilient = new ConnectionRegistry(broken);
        resilient.register("browser", ConnectionType.NATIVE_ANDROID);
        resilient.markConnected("browser", connectedAt);
        check(resilient.get("browser").orElseThrow().connected(),
                "persistence failure must not break in-process connection state");

        System.out.println("ConnectionRegistryPersistenceTest passed");
    }

    private static final class MapPersistence implements ConnectionRegistryPersistence {
        private final Map<String,ConnectionState> values = new LinkedHashMap<>();
        @Override public Map<String,ConnectionState> load() { return Map.copyOf(values); }
        @Override public void put(ConnectionState state) { values.put(state.id(), state); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
