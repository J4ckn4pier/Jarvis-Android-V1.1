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
        check(source.contains("Executors.newSingleThreadExecutor")
                        && source.contains("brainExecutor.execute(")
                        && source.contains("ui.post(() -> deliverPresentation("),
                "full-app provider/tool work must run off Android's main thread and marshal presentation back to the UI thread");
        check(source.contains("private boolean destroyed;")
                        && source.contains("if (destroyed) return;")
                        && source.contains("destroyed = true;"),
                "late background results must not update or speak through a destroyed full-app Activity");
        check(source.contains("brainExecutor.shutdownNow()"),
                "MainActivity destruction must stop its background brain executor");
        check(!source.contains("brain.handle(command, this::deliverResult)"),"production command path must not call legacy JarvisBrain callback");
        check(!source.contains("brain.handleCandidates(candidates, this::deliverResult)"),"recognized candidates must not call legacy JarvisBrain callback");
        check(!source.contains("import com.jarvis.mobile.brain.JarvisBrain;"),"MainActivity must not import legacy JarvisBrain");
        check(!source.contains("private JarvisBrain brain;"),"MainActivity must not own legacy JarvisBrain");
        check(!source.contains("new JarvisBrain(this)"),"MainActivity must not instantiate legacy JarvisBrain");
        System.out.println("MainActivitySharedRuntimeContractTest passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
