package com.jarvis.brain;
import java.time.Instant;
public final class ManualMemoryEditorTest {
 public static void main(String[] args){
  LongTermMemoryStore s=new LongTermMemoryStore(); ManualMemoryEditor e=new ManualMemoryEditor(s); Instant a=Instant.parse("2026-08-28T18:00:00Z"), b=a.plusSeconds(60), c=b.plusSeconds(60);
  e.addOrReplace("pref:coffee",MemoryType.PREFERENCE,"prefers dark roast",a); RichMemory m=s.current("pref:coffee",a).orElseThrow(); check(m.source().equals("manual-user-entry"),"manual provenance distinct"); check(m.confidence()==1.0,"manual explicit truth trusted");
  e.addOrReplace("pref:coffee",MemoryType.PREFERENCE,"prefers medium roast",b); check(s.current("pref:coffee",b).orElseThrow().content().contains("medium"),"edit supersedes"); check(s.history("pref:coffee").size()==2,"edit preserves history");
  e.remove("pref:coffee",c); check(s.current("pref:coffee",c).isEmpty(),"remove leaves no current memory"); check(s.history("pref:coffee").size()==2,"remove preserves audit history");
  System.out.println("ManualMemoryEditorTest passed");
 }
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
