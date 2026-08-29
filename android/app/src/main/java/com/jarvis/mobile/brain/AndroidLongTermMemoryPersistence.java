package com.jarvis.mobile.brain;

import com.jarvis.brain.LongTermMemoryPersistence;
import com.jarvis.brain.LongTermMemoryStore;
import com.jarvis.brain.MemoryCipher;
import com.jarvis.brain.RichMemory;
import com.jarvis.brain.RichMemoryPersistence;
import java.nio.file.Path;
import java.util.List;

/** App-private encrypted persistence adapter for JARVIS rich long-term memory. */
public final class AndroidLongTermMemoryPersistence implements LongTermMemoryPersistence {
    private final Path file;
    private final MemoryCipher cipher;

    public AndroidLongTermMemoryPersistence(Path file, MemoryCipher cipher) {
        if (file == null) throw new IllegalArgumentException("memory file required");
        if (cipher == null) throw new IllegalArgumentException("memory cipher required");
        this.file = file;
        this.cipher = cipher;
    }

    @Override
    public List<RichMemory> load() {
        return RichMemoryPersistence.loadEncrypted(file, cipher).snapshotAll();
    }

    @Override
    public void save(List<RichMemory> memories) {
        LongTermMemoryStore snapshot = new LongTermMemoryStore();
        if (memories != null) for (RichMemory memory : memories) if (memory != null) snapshot.put(memory);
        RichMemoryPersistence.saveEncrypted(snapshot, file, cipher);
    }
}
