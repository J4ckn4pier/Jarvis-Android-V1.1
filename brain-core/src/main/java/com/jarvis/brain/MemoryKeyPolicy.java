package com.jarvis.brain;

/** Mechanical release safeguard so test/static key material cannot silently reach a production memory build. */
public final class MemoryKeyPolicy {
    private MemoryKeyPolicy() {}

    public static void requireSafe(BuildMode mode, MemoryKeySource source) {
        if (mode == null) throw new IllegalArgumentException("build mode required");
        if (source == null) throw new IllegalArgumentException("memory key source required");
        if (mode == BuildMode.RELEASE && source != MemoryKeySource.ANDROID_KEYSTORE) {
            throw new IllegalStateException("Release memory requires Android Keystore-backed key material");
        }
    }
}
