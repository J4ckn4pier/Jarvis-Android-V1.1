package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Static contract for durable non-secret Android connection/auth state. */
public final class AndroidConnectionRegistryPersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidConnectionRegistryPersistence.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(adapterPath), "Android must persist non-secret connection/auth state");
        String adapter = Files.readString(adapterPath);
        String lower = adapter.toLowerCase();
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements ConnectionRegistryPersistence"),
                "Android connection adapter must implement the shared persistence port");
        check(adapter.contains("MODE_PRIVATE"), "connection state must use app-private storage");
        check(adapter.contains("jarvis_connection_state"), "connection state needs a dedicated namespace");
        check(!lower.contains("access_token") && !lower.contains("refresh_token") && !lower.contains("api_key")
                        && !adapter.contains("SecureSecretStore"),
                "connection-state persistence must never absorb credential material");
        check(runtime.contains("new ConnectionRegistry(new AndroidConnectionRegistryPersistence(app))"),
                "Android runtime must compose the durable connection registry");

        System.out.println("AndroidConnectionRegistryPersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
