package com.jarvis.brain;
import java.time.*;
public final class ResponseStyleContractTest{
 public static void main(String[]a){String g=ResponseStyleContract.beta().guidance();check(g.contains("sir"),"beta style explicitly permits sir");check(g.toLowerCase().contains("understated"),"understated tone required");check(g.toLowerCase().contains("precise"),"precise tone required");check(g.toLowerCase().contains("concise"),"concise tone required");BrainEngine b=BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-29T04:00:00Z"),ZoneOffset.UTC));final ReasoningRequest[] seen={null};AssistantCore c=new AssistantCore(b,r->{seen[0]=r;return new ReasoningResult("test","Very good, sir.",null);},ToolRegistry.standard());c.handle("Hey Jarvis");c.handle("consider a complicated hypothetical");check(seen[0]!=null&&seen[0].context().contains(ResponseStyleContract.beta().guidance()),"every provider reasoning request receives one central style contract");System.out.println("ResponseStyleContractTest passed");}
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
