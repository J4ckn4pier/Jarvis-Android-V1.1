package com.jarvis.brain;

import java.time.Instant;
import java.util.*;

/** Stores non-secret connection/auth state. Credential material remains in platform secure storage. */
public final class ConnectionRegistry {
    private final Map<String,ConnectionState> states = new HashMap<>();
    private final ConnectionRegistryPersistence persistence;

    public ConnectionRegistry() {
        this(ConnectionRegistryPersistence.none());
    }

    public ConnectionRegistry(ConnectionRegistryPersistence persistence) {
        this.persistence = persistence == null ? ConnectionRegistryPersistence.none() : persistence;
        try {
            Map<String,ConnectionState> restored = this.persistence.load();
            if (restored != null) {
                for (ConnectionState state : restored.values()) {
                    if (state != null) states.put(state.id(), state);
                }
            }
        } catch (RuntimeException ignored) {
            // Runtime connection management remains usable when persistence is unavailable.
        }
    }

    public synchronized void register(String id, ConnectionType type) {
        ConnectionState state = new ConnectionState(id, type, false, null);
        states.put(id, state);
        persist(state);
    }

    public synchronized void markConnected(String id, Instant at) {
        ConnectionState existing = require(id);
        ConnectionState state = new ConnectionState(
                id, existing.type(), true, at == null ? Instant.now() : at);
        states.put(id, state);
        persist(state);
    }

    public synchronized void disconnect(String id) {
        ConnectionState existing = require(id);
        ConnectionState state = new ConnectionState(id, existing.type(), false, null);
        states.put(id, state);
        persist(state);
    }

    public synchronized Optional<ConnectionState> get(String id) {
        return Optional.ofNullable(states.get(id));
    }

    public synchronized List<ConnectionState> all() {
        return states.values().stream().sorted(Comparator.comparing(ConnectionState::id)).toList();
    }

    private ConnectionState require(String id) {
        ConnectionState state = states.get(id);
        if (state == null) throw new IllegalArgumentException("unknown connection: " + id);
        return state;
    }

    private void persist(ConnectionState state) {
        try { persistence.put(state); } catch (RuntimeException ignored) { }
    }
}
