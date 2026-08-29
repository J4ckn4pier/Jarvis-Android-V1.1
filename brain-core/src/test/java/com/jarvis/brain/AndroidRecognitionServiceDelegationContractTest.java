package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** The assistant's required RecognitionService declaration must not become a dead recognizer on API 29/30. */
public final class AndroidRecognitionServiceDelegationContractTest {
    public static void main(String[] args) throws Exception {
        String service = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/assistant/JarvisRecognitionService.java"));

        check(!service.contains("listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);"),
                "recognition service must not unconditionally reject every request as busy");
        check(service.contains("queryIntentServices"),
                "legacy compatibility path must discover an external Android RecognitionService provider");
        check(service.contains("!serviceInfo.packageName.equals(getPackageName())"),
                "provider discovery must exclude JARVIS itself to prevent recursive recognition binding");
        check(service.contains("SpeechRecognizer.createSpeechRecognizer(this, component)"),
                "legacy compatibility path must target the selected external recognition provider explicitly");
        check(service.contains("new RecognitionListener()"),
                "legacy compatibility path must forward the external provider callback stream");
        check(service.contains("listener.readyForSpeech(params)"), "ready callback must be forwarded");
        check(service.contains("listener.beginningOfSpeech()"), "speech-start callback must be forwarded");
        check(service.contains("listener.partialResults(partialResults)"), "partial results must be forwarded");
        check(service.contains("listener.results(results)"), "final results must be forwarded");
        check(service.contains("listener.error(error)"), "recognition failures must remain truthful");
        check(service.contains("recognizer.stopListening()"), "framework stop must reach delegated recognizer");
        check(service.contains("recognizer.cancel()"), "framework cancel must reach delegated recognizer");
        check(service.contains("recognizer.destroy()"), "delegated recognizers must be released");

        System.out.println("AndroidRecognitionServiceDelegationContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
