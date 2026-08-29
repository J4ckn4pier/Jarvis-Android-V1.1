package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;
import com.jarvis.brain.ConnectionRegistryPersistence;
import com.jarvis.brain.ConnectionState;
import com.jarvis.brain.ConnectionType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** App-private persistence for non-secret connection/auth state only. */
public final class AndroidConnectionRegistryPersistence implements ConnectionRegistryPersistence {
    private static final String NAME = "jarvis_connection_state";
    private final SharedPreferences preferences;

    public AndroidConnectionRegistryPersistence(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        preferences = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    @Override
    public Map<String,ConnectionState> load() {
        Map<String,ConnectionState> states = new LinkedHashMap<>();
        for (Map.Entry<String,?> entry : preferences.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String encoded)) continue;
            try {
                String[] parts = encoded.split("\\|", -1);
                if (parts.length != 3) continue;
                ConnectionType type = ConnectionType.valueOf(parts[0]);
                boolean connected = Boolean.parseBoolean(parts[1]);
                Instant authenticatedAt = connected && !parts[2].isBlank() ? Instant.parse(parts[2]) : null;
                states.put(entry.getKey(), new ConnectionState(entry.getKey(), type, connected, authenticatedAt));
            } catch (RuntimeException ignored) {
                // Malformed state is ignored; credentials are never stored here.
            }
        }
        return Map.copyOf(states);
    }

    @Override
    public void put(ConnectionState state) {
        if (state == null) throw new IllegalArgumentException("state required");
        String timestamp = state.authenticatedAt() == null ? "" : state.authenticatedAt().toString();
        String encoded = state.type().name() + "|" + state.connected() + "|" + timestamp;
        preferences.edit().putString(state.id(), encoded).apply();
    }
}
