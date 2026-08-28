package com.jarvis.brain;
import java.time.Instant;
public record ConnectionState(String id, ConnectionType type, boolean connected, Instant authenticatedAt) {
 public ConnectionState { if(id==null||id.isBlank()) throw new IllegalArgumentException("id required"); if(type==null) throw new IllegalArgumentException("type required"); if(!connected) authenticatedAt=null; }
}
