package com.jarvis.brain;

import java.time.Instant;
import java.util.Set;

/** User-directed memory CRUD boundary. Manual entries remain explicit user-authored truth. */
public final class ManualMemoryEditor {
    private final LongTermMemoryStore store;
    public ManualMemoryEditor(LongTermMemoryStore store) { if (store == null) throw new IllegalArgumentException("store required"); this.store = store; }
    public void addOrReplace(String key, MemoryType type, String content, Instant when) {
        String k = require(key,"key"), c = require(content,"content");
        Instant at = when == null ? Instant.now() : when;
        store.put(new RichMemory(k, type, c, "manual-user-entry", 1.0, 0.90, at, null, Set.of("manual-entry","user-authored")));
    }
    public void remove(String key, Instant when) {
        String k=require(key,"key");
        Instant at=when==null?Instant.now():when;
        store.archive(k, at);
    }
    private static String require(String v,String label){String s=v==null?"":v.trim();if(s.isBlank())throw new IllegalArgumentException(label+" required");return s;}
}
