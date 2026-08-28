package com.jarvis.brain;

import java.time.Instant;
import java.util.Map;

public record ActivityRecord(String id,Instant at,String title,Status status,String detail,Map<String,String> evidence){
 public enum Status{DONE,NEEDS_INPUT,FAILED}
 public ActivityRecord{if(id==null||id.isBlank())throw new IllegalArgumentException("id required");at=at==null?Instant.now():at;if(title==null||title.isBlank())throw new IllegalArgumentException("title required");if(status==null)throw new IllegalArgumentException("status required");detail=detail==null?"":detail.trim();evidence=evidence==null?Map.of():Map.copyOf(evidence);}
}
