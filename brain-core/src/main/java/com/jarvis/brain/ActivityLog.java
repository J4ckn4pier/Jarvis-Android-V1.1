package com.jarvis.brain;

import java.util.*;

/** Transparency/audit log for autonomous and user-approved actions. */
public final class ActivityLog {
 private final LinkedHashMap<String,ActivityRecord> records=new LinkedHashMap<>();
 private final ActivityLogPersistence persistence;
 public ActivityLog(){this(ActivityLogPersistence.none());}
 public ActivityLog(ActivityLogPersistence persistence){this.persistence=persistence==null?ActivityLogPersistence.none():persistence;restore();}
 public synchronized void append(ActivityRecord record){if(record==null)throw new IllegalArgumentException("record required");records.put(record.id(),record);try{persistence.put(record);}catch(RuntimeException ignored){}}
 public synchronized List<ActivityRecord> all(){List<ActivityRecord> out=new ArrayList<>(records.values());Collections.reverse(out);return List.copyOf(out);}
 public synchronized List<ActivityRecord> needsAttention(){return all().stream().filter(r->r.status()!=ActivityRecord.Status.DONE).toList();}
 public synchronized Optional<ActivityRecord> get(String id){return Optional.ofNullable(records.get(id==null?"":id.trim()));}
 public synchronized boolean remove(String id){String clean=id==null?"":id.trim();boolean removed=records.remove(clean)!=null;if(removed){try{persistence.remove(clean);}catch(RuntimeException ignored){}}return removed;}
 private void restore(){Map<String,ActivityRecord> restored;try{restored=persistence.load();}catch(RuntimeException ignored){return;}if(restored==null)return;for(ActivityRecord record:restored.values()){if(record!=null)records.put(record.id(),record);}}
}
