package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards the production full-app command path from drifting back to the legacy Android intent engine. */
public final class MainActivitySharedRuntimeContractTest {
    public static void main(String[] args) throws Exception {
        Path sourcePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/MainActivity.java");
        String source = Files.readString(sourcePath);
        check(source.contains("import com.jarvis.mobile.brain.AndroidBrainRuntime;"),"MainActivity must import AndroidBrainRuntime");
        check(source.contains("private AndroidBrainRuntime runtime;"),"MainActivity must own the shared Android runtime");
        check(source.contains("runtime.handlePresentation(command)"),"typed/recognized commands must enter RuntimeSurfacePresentation");
        check(source.contains("FullAppRuntimeViewState.from(presentation)"),"MainActivity must project runtime state through the shared full-app contract");
        check(source.contains("JARVIS_SHARED_BRAIN_ACTIVE"),"MainActivity must emit the emulator shared-brain evidence marker");
        check(!source.contains("brain.handle(command, this::deliverResult)"),"production command path must not call legacy JarvisBrain callback");
        check(!source.contains("brain.handleCandidates(candidates, this::deliverResult)"),"recognized candidates must not call legacy JarvisBrain callback");
        check(!source.contains("import com.jarvis.mobile.brain.JarvisBrain;"),"MainActivity must not import legacy JarvisBrain");
        check(!source.contains("private JarvisBrain brain;"),"MainActivity must not own legacy JarvisBrain");
        check(!source.contains("new JarvisBrain(this)"),"MainActivity must not instantiate legacy JarvisBrain");
        System.out.println("MainActivitySharedRuntimeContractTest passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
