package com.jarvis.brain;
import java.time.Instant; import java.util.*;
/** Routes connect/disconnect through a type-matched platform port and updates state only on reported success. */
public final class ConnectionManager{
 private final ConnectionRegistry registry; private final Map<ConnectionType,ConnectionPort> ports;
 public ConnectionManager(ConnectionRegistry registry,List<ConnectionPort> ports){this.registry=Objects.requireNonNull(registry);Map<ConnectionType,ConnectionPort> m=new EnumMap<>(ConnectionType.class);if(ports!=null)for(ConnectionPort p:ports)if(p!=null)m.put(p.type(),p);this.ports=Map.copyOf(m);}
 public ToolResult connect(String id,Instant now){ConnectionState s=registry.get(id).orElseThrow(()->new IllegalArgumentException("unknown connection: "+id));ConnectionPort p=ports.get(s.type());if(p==null)return ToolResult.failure("no connection adapter for "+s.type());ToolResult r=p.beginConnect(id);if(r.status()==ToolResult.Status.SUCCESS)registry.markConnected(id,now);return r;}
 public ToolResult disconnect(String id){ConnectionState s=registry.get(id).orElseThrow(()->new IllegalArgumentException("unknown connection: "+id));ConnectionPort p=ports.get(s.type());if(p==null)return ToolResult.failure("no connection adapter for "+s.type());ToolResult r=p.disconnect(id);if(r.status()==ToolResult.Status.SUCCESS)registry.disconnect(id);return r;}
}
