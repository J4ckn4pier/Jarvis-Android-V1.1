package com.jarvis.brain;
import java.nio.file.*;
/** Production manifests/themes must use original JARVIS naming, not donor-era labels. */
public final class CleanRoomNamingContractTest{
 public static void main(String[]a)throws Exception{String manifest=Files.readString(Path.of("../android/app/src/main/AndroidManifest.xml"));String styles=Files.readString(Path.of("../android/app/src/main/res/values/styles.xml"));String combined=(manifest+"\n"+styles).toLowerCase();check(!combined.contains("donor"),"production manifest/styles must not contain donor naming");check(styles.contains("JarvisPanelTheme"),"original panel theme required");System.out.println("CleanRoomNamingContractTest passed");}
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
