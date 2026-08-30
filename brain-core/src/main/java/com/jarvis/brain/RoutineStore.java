package com.jarvis.brain;

import java.util.*;

/** Editable routine registry. Trigger execution is policy-gated elsewhere. */
public final class RoutineStore {
 private final LinkedHashMap<String,RoutineDefinition> routines=new LinkedHashMap<>();
 private final RoutineStorePersistence persistence;
 public RoutineStore(){this(RoutineStorePersistence.none());}
 public RoutineStore(RoutineStorePersistence persistence){this.persistence=persistence==null?RoutineStorePersistence.none():persistence;restore();}
 public synchronized void upsert(RoutineDefinition routine){if(routine==null)throw new IllegalArgumentException("routine required");routines.put(routine.id(),routine);try{persistence.put(routine);}catch(RuntimeException ignored){}}
 public synchronized Optional<RoutineDefinition> get(String id){return Optional.ofNullable(routines.get(id==null?"":id.trim()));}
 public synchronized List<RoutineDefinition> all(){return List.copyOf(routines.values());}
 public synchronized boolean remove(String id){String clean=id==null?"":id.trim();boolean removed=routines.remove(clean)!=null;if(removed){try{persistence.remove(clean);}catch(RuntimeException ignored){}}return removed;}
 public synchronized RoutineDefinition setEnabled(String id,boolean enabled){RoutineDefinition r=get(id).orElseThrow(()->new IllegalArgumentException("unknown routine: "+id));RoutineDefinition updated=r.withEnabled(enabled);upsert(updated);return updated;}
 public synchronized List<RoutineDefinition> matching(String triggerType){String t=triggerType==null?"":triggerType.trim();return routines.values().stream().filter(RoutineDefinition::enabled).filter(r->r.triggerType().equalsIgnoreCase(t)).toList();}
 private void restore(){Map<String,RoutineDefinition> restored;try{restored=persistence.load();}catch(RuntimeException ignored){return;}if(restored==null)return;for(RoutineDefinition routine:restored.values()){if(routine!=null)routines.put(routine.id(),routine);}}
}
