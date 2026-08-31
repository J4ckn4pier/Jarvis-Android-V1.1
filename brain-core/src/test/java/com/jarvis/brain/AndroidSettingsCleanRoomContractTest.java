package com.jarvis.brain;
import java.nio.file.*;
/** Production settings may expose current JARVIS capabilities only, not removed donor audio/theme toggles. */
public final class AndroidSettingsCleanRoomContractTest{
 public static void main(String[]a)throws Exception{
  String s=Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/SettingsActivity.java"));
  String lower=s.toLowerCase();
  check(!lower.contains("legacy jarvis audio cues"),"removed donor audio pack must not have a settings toggle");
  check(!lower.contains("legacy_cues"),"removed donor cue preference must not remain");
  check(!lower.contains("mark ii"),"removed donor Mark II theme must not remain selectable");
  check(!lower.contains("mark iii"),"removed donor Mark III theme must not remain selectable");
  check(!lower.contains("mark_theme"),"removed donor theme preference must not remain");
  check(!lower.contains("donor settings roles"),"source documentation must use current JARVIS ownership");
  check(s.contains("Free Local AI (Ollama)"),"normal Settings must expose the zero-token local AI path without Developer Options");
  check(s.contains("CortexProviderFactory.MODE_OPENAI_COMPATIBLE"),"local AI setup must select the provider-neutral OpenAI-compatible transport");
  check(s.contains("http://jarvis-cortex.local:11434/v1/chat/completions"),"phone-safe local AI setup must default to an editable LAN .local endpoint rather than Android loopback");
  check(s.contains("qwen3:4b"),"local AI setup must provide a concrete lightweight default model instead of requiring model-name knowledge");
  check(s.contains("Local AI server"),"normal Settings must let the user edit the local server address");
  System.out.println("AndroidSettingsCleanRoomContractTest passed");
 }
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
