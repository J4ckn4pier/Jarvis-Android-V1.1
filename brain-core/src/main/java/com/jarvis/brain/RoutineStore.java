package com.jarvis.brain;

import java.util.*;

/** Editable routine registry. Trigger execution is policy-gated elsewhere. */
public final class RoutineStore {
 private final LinkedHashMap<String,RoutineDefinition> routines=new LinkedHashMap<>();
 public synchronized void upsert(RoutineDefinition routine){if(routine==null)throw new IllegalArgumentException("routine required");routines.put(routine.id(),routine);}
 public synchronized Optional<RoutineDefinition> get(String id){return Optional.ofNullable(routines.get(id==null?"":id.trim()));}
 public synchronized List<RoutineDefinition> all(){return List.copyOf(routines.values());}
 public synchronized boolean remove(String id){return routines.remove(id==null?"":id.trim())!=null;}
 public synchronized RoutineDefinition setEnabled(String id,boolean enabled){RoutineDefinition r=get(id).orElseThrow(()->new IllegalArgumentException("unknown routine: "+id));RoutineDefinition updated=r.withEnabled(enabled);routines.put(updated.id(),updated);return updated;}
 public synchronized List<RoutineDefinition> matching(String triggerType){String t=triggerType==null?"":triggerType.trim();return routines.values().stream().filter(RoutineDefinition::enabled).filter(r->r.triggerType().equalsIgnoreCase(t)).toList();}
}
