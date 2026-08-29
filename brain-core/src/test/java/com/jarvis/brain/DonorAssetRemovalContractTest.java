package com.jarvis.brain;
import java.nio.file.*;
/** Superseding clean-room gate: donor visual/audio files must be physically absent from source and release verification. */
public final class DonorAssetRemovalContractTest{
 public static void main(String[]a)throws Exception{
  Path res=Path.of("../android/app/src/main/res");String workflow=Files.readString(Path.of("../.github/workflows/build-apk.yml"));String activity=Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java"));
  check(!Files.exists(res.resolve("raw")),"legacy donor raw audio directory must be removed from source");
  check(!Files.exists(res.resolve("drawable-nodpi")),"legacy donor nodpi drawable pack must be removed from source");
  check(!Files.exists(res.resolve("drawable-hdpi/ic_reactor.png")),"donor reactor drawable must be removed");
  check(!Files.exists(res.resolve("drawable-xxhdpi/menu_dots.png")),"donor menu drawable must be removed");
  check(!workflow.contains("test \"$(find android/app/src/main/res/raw -maxdepth 1 -type f -name '*.mp3' | wc -l)\" -eq 100"),"CI must not require donor MP3 pack");
  check(!workflow.contains("grep -q 'jarvis_normal'"),"CI must not require donor reactor sprite");
  check(!workflow.contains("grep -q 'background_mk3_active'"),"CI must not require donor background");
  check(workflow.contains("DONOR_CONTAMINATION_CHECK"),"release workflow must verify donor contamination absence in compiled APK");
  check(!activity.contains("R.drawable.background_mk2"),"MainActivity must not require donor MKII assets");
  check(!activity.contains("R.drawable.background_mk3"),"MainActivity must not require donor MKIII assets");
  check(!activity.contains("R.drawable.jarvis_normal"),"MainActivity must not require donor reactor assets");
  check(!activity.contains("LegacyResponsePlayer"),"MainActivity must not retain donor audio player");
  System.out.println("DonorAssetRemovalContractTest passed");
 }
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
