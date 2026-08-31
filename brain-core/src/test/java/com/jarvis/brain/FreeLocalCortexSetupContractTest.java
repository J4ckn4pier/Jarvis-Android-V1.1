package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Windows setup must provision a no-metered-cost local model and expose only the private LAN endpoint needed by Android. */
public final class FreeLocalCortexSetupContractTest {
    public static void main(String[] args) throws Exception {
        Path scriptPath = Path.of("../tools/local-cortex/setup-windows.ps1");
        check(Files.exists(scriptPath), "Windows local-cortex setup script must exist");
        String script = Files.readString(scriptPath);
        check(script.contains("qwen3:4b-instruct"), "setup must use the approved lightweight Apache-2.0 default model");
        check(script.contains("OLLAMA_HOST"), "setup must configure Ollama for phone-to-PC LAN access");
        check(script.contains("0.0.0.0:11434"), "setup must bind the local cortex on Ollama's LAN port");
        check(script.contains("ollama pull"), "setup must pull the model locally rather than require a hosted API");
        check(script.contains("11434/v1/chat/completions"), "setup must print the exact Android OpenAI-compatible endpoint");
        check(!script.toLowerCase().contains("api key required"), "local setup must not require a paid/provider API key");
        System.out.println("FreeLocalCortexSetupContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
