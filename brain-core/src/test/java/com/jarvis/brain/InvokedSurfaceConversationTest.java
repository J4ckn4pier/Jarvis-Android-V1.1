package com.jarvis.brain;
import java.time.*;
/** An explicitly opened assistant UI/session is already directed speech; it must not require a wake phrase again. */
public final class InvokedSurfaceConversationTest{
 public static void main(String[]a){BrainEngine brain=BrainEngine.createDefault(Clock.fixed(Instant.parse("2026-08-29T04:00:00Z"),ZoneOffset.UTC));BrainResponse ambient=brain.handle("help me!!!");check(ambient.kind()==BrainResponse.Kind.IGNORED_AMBIENT,"sleeping ambient speech stays ignored");brain.beginInvokedConversation();BrainResponse direct=brain.handle("help me!!!");check(direct.kind()==BrainResponse.Kind.CONVERSATION,"invoked surface accepts direct input");check(direct.text().startsWith("You can speak naturally"),"help response preserved");System.out.println("InvokedSurfaceConversationTest passed");}
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
