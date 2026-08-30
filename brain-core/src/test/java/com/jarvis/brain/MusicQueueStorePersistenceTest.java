package com.jarvis.brain;

import java.util.List;

/** Proves UI music queue/playback state survives restart while transport remains platform-owned. */
public final class MusicQueueStorePersistenceTest {
    public static void main(String[] args) {
        MemoryPersistence persistence = new MemoryPersistence();
        MusicTrack firstTrack = new MusicTrack("track-1", "First", "Artist A", 240);
        MusicTrack secondTrack = new MusicTrack("track-2", "Second", "Artist B", 300);

        MusicQueueStore first = new MusicQueueStore(persistence);
        first.add(firstTrack);
        first.add(secondTrack);
        first.play("track-2");
        first.seek(42);
        first.setVolume(33);
        first.setShuffle(true);
        first.setRepeat(true);

        MusicQueueStore afterRestart = new MusicQueueStore(persistence);
        check(afterRestart.queue().equals(List.of(firstTrack, secondTrack)), "queue order must survive restart");
        check(afterRestart.current().orElseThrow().id().equals("track-2"), "current track must survive restart");
        MusicQueueStore.PlaybackState restored = afterRestart.state();
        check(restored.playing(), "playing state must survive restart");
        check(restored.shuffle(), "shuffle state must survive restart");
        check(restored.repeat(), "repeat state must survive restart");
        check(restored.volume() == 33, "volume must survive restart");
        check(restored.positionSeconds() == 42, "position must survive restart");

        afterRestart.remove("track-2");
        MusicQueueStore afterRemovalRestart = new MusicQueueStore(persistence);
        check(afterRemovalRestart.queue().equals(List.of(firstTrack)), "track removal must persist");
        check(afterRemovalRestart.current().orElseThrow().id().equals("track-1"),
                "current track must be repaired truthfully after persisted removal");

        MusicQueueStorePersistence broken = new MusicQueueStorePersistence() {
            @Override public Snapshot load() { throw new IllegalStateException("unavailable"); }
            @Override public void save(Snapshot snapshot) { throw new IllegalStateException("unavailable"); }
        };
        MusicQueueStore resilient = new MusicQueueStore(broken);
        resilient.add(firstTrack);
        resilient.play("track-1");
        resilient.setVolume(18);
        check(resilient.current().orElseThrow().id().equals("track-1"),
                "persistence failure must not erase truthful in-process queue state");
        check(resilient.state().playing() && resilient.state().volume() == 18,
                "persistence failure must not block in-process playback controls");

        System.out.println("MusicQueueStorePersistenceTest passed");
    }

    private static final class MemoryPersistence implements MusicQueueStorePersistence {
        private Snapshot snapshot = Snapshot.empty();
        @Override public Snapshot load() { return snapshot; }
        @Override public void save(Snapshot snapshot) { this.snapshot = snapshot; }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
