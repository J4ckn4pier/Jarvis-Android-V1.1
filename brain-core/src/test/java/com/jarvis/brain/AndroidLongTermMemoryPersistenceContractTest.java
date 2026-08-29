package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Static Android composition contract for one encrypted durable memory source shared by reasoning and UI. */
public final class AndroidLongTermMemoryPersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidLongTermMemoryPersistence.java");
        Path cipherPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidKeystoreMemoryCipher.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(adapterPath), "Android must provide encrypted long-term memory persistence");
        String adapter = Files.readString(adapterPath);
        String cipher = Files.readString(cipherPath);
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements LongTermMemoryPersistence"), "Android memory adapter must implement shared persistence port");
        check(adapter.contains("RichMemoryPersistence.loadEncrypted"), "Android memory restore must decrypt via shared rich-memory codec");
        check(adapter.contains("RichMemoryPersistence.saveEncrypted"), "Android memory writes must encrypt via shared rich-memory codec");
        check(runtime.contains("long-term-memory.bin"), "Android runtime must use a dedicated app-private long-term memory file");
        check(runtime.contains("getNoBackupFilesDir()"), "personal memory must live in app-private no-backup storage");
        check(runtime.contains("new AndroidKeystoreMemoryCipher(\"jarvis.long.term.memory.v1\")"), "personal memory must use a dedicated Keystore alias");
        check(runtime.contains("LongTermMemoryStore memory = new LongTermMemoryStore"), "runtime must own one durable long-term memory store");
        check(runtime.contains("MemoryContextSource memoryContext = new MemoryContextSource(memory"), "reasoning context must read the durable runtime-owned memory store");
        check(runtime.contains("new CompositeAssistantContextSource"), "durable memory must remain part of a composed provider-neutral context source");
        check(runtime.contains("memoryContext,"), "composed reasoning context must include the durable memory source");
        check(runtime.contains("new AssistantCore(brain, reasoning, tools, runtimeContext)"), "AssistantCore must receive the composed context containing durable memory");
        check(runtime.contains("new JarvisUiBackend(memory, tools"), "UI must share the exact runtime-owned memory store");
        check(cipher.contains("AndroidKeystoreMemoryCipher(String alias)"), "Keystore cipher must support purpose-specific aliases");
        check(!cipher.contains("getEncoded()"), "Keystore keys must remain non-exportable");

        System.out.println("AndroidLongTermMemoryPersistenceContractTest: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
