package com.jarvis.brain;

import java.util.List;

/** Persistence boundary for UI music queue/playback state; audio transport remains platform-owned. */
public interface MusicQueueStorePersistence {
    Snapshot load();
    void save(Snapshot snapshot);

    record Snapshot(
            List<MusicTrack> queue,
            String currentId,
            boolean playing,
            boolean shuffle,
            boolean repeat,
            int volume,
            long positionSeconds) {
        public Snapshot {
            queue = queue == null ? List.of() : List.copyOf(queue);
            currentId = currentId == null ? "" : currentId.trim();
            volume = Math.max(0, Math.min(100, volume));
            positionSeconds = Math.max(0, positionSeconds);
        }
        public static Snapshot empty() { return new Snapshot(List.of(), "", false, false, false, 70, 0); }
    }

    static MusicQueueStorePersistence none() {
        return new MusicQueueStorePersistence() {
            @Override public Snapshot load() { return Snapshot.empty(); }
            @Override public void save(Snapshot snapshot) { }
        };
    }
}
