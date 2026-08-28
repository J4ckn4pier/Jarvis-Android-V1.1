package com.jarvis.brain;
import java.time.Instant;
public final class ConnectionRegistryTest {
 public static void main(String[] args){
  ConnectionRegistry r=new ConnectionRegistry(); r.register("opentable",ConnectionType.WEB_OAUTH); r.register("maps",ConnectionType.NATIVE_ANDROID); r.register("openai",ConnectionType.AI_PROVIDER);
  check(!r.get("opentable").orElseThrow().connected(),"starts disconnected");
  Instant t=Instant.parse("2026-08-28T18:00:00Z"); r.markConnected("opentable",t); check(r.get("opentable").orElseThrow().connected(),"connected"); check(t.equals(r.get("opentable").orElseThrow().authenticatedAt()),"auth provenance retained");
  r.disconnect("opentable"); check(!r.get("opentable").orElseThrow().connected(),"disconnects"); check(r.get("opentable").orElseThrow().authenticatedAt()==null,"disconnect clears auth timestamp");
  System.out.println("ConnectionRegistryTest passed");
 }
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
