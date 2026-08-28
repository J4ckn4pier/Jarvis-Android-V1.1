package com.jarvis.brain;

import java.util.*;

/** Editable draft state only. Sending is intentionally a separate consequential tool. */
public final class DraftMessageStore {
    private final LinkedHashMap<String,MessageDraft> drafts = new LinkedHashMap<>();
    public synchronized void save(MessageDraft draft){ if(draft==null)throw new IllegalArgumentException("draft required");drafts.put(draft.id(),draft); }
    public synchronized Optional<MessageDraft> get(String id){ return Optional.ofNullable(drafts.get(clean(id))); }
    public synchronized List<MessageDraft> all(){ return List.copyOf(drafts.values()); }
    public synchronized boolean remove(String id){ return drafts.remove(clean(id)) != null; }
    private static String clean(String value){return value==null?"":value.trim();}
}
