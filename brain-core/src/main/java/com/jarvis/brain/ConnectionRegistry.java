package com.jarvis.brain;
import java.time.Instant; import java.util.*;
/** Stores non-secret connection/auth state. Credential material remains in platform secure storage. */
public final class ConnectionRegistry {
 private final Map<String,ConnectionState> states=new HashMap<>();
 public void register(String id, ConnectionType type){ states.put(id,new ConnectionState(id,type,false,null)); }
 public void markConnected(String id, Instant at){ ConnectionState s=require(id); states.put(id,new ConnectionState(id,s.type(),true,at==null?Instant.now():at)); }
 public void disconnect(String id){ ConnectionState s=require(id); states.put(id,new ConnectionState(id,s.type(),false,null)); }
 public Optional<ConnectionState> get(String id){return Optional.ofNullable(states.get(id));}
 public List<ConnectionState> all(){return states.values().stream().sorted(Comparator.comparing(ConnectionState::id)).toList();}
 private ConnectionState require(String id){ConnectionState s=states.get(id);if(s==null)throw new IllegalArgumentException("unknown connection: "+id);return s;}
}
