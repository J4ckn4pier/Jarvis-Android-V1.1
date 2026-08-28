package com.jarvis.brain;

import java.util.Map;

/** User-programmable when/then automation definition. */
public record RoutineDefinition(String id,String title,String triggerType,Map<String,String> triggerArguments,Plan actionPlan,boolean enabled){
 public RoutineDefinition{if(id==null||id.isBlank())throw new IllegalArgumentException("id required");if(title==null||title.isBlank())throw new IllegalArgumentException("title required");if(triggerType==null||triggerType.isBlank())throw new IllegalArgumentException("trigger type required");triggerArguments=triggerArguments==null?Map.of():Map.copyOf(triggerArguments);if(actionPlan==null)throw new IllegalArgumentException("action plan required");}
 public RoutineDefinition withEnabled(boolean value){return new RoutineDefinition(id,title,triggerType,triggerArguments,actionPlan,value);}
}
