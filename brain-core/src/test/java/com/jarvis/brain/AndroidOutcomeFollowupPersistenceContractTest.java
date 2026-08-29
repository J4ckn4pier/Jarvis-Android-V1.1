package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Static Android composition contract for encrypted, app-private pending outcome follow-ups. */
public final class AndroidOutcomeFollowupPersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path cipherPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidKeystoreMemoryCipher.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(cipherPath), "Android production must provide a Keystore-backed MemoryCipher");
        String cipher = Files.readString(cipherPath);
        String runtime = Files.readString(runtimePath);

        check(cipher.contains("implements MemoryCipher"), "Android cipher must implement the shared MemoryCipher port");
        check(cipher.contains("AndroidKeyStore"), "Android cipher must keep follow-up encryption key in Android Keystore");
        check(cipher.contains("KeyGenParameterSpec"), "Android cipher must generate a non-exportable platform key");
        check(cipher.contains("AES/GCM/NoPadding"), "Android cipher must use authenticated AES-GCM encryption");
        check(!cipher.contains("getEncoded()"), "Android Keystore key must never be exported to raw bytes");
        check(!cipher.contains("TEST_STATIC"), "Android production cipher must not use static/test key material");

        check(runtime.contains("getNoBackupFilesDir()"), "pending follow-ups must live in an app-private no-backup directory");
        check(runtime.contains("pending-outcome-followups.bin"), "Android runtime must bind a dedicated durable pending-followup file");
        check(runtime.contains("AndroidKeystoreMemoryCipher"), "Android runtime must compose the Keystore-backed cipher");
        check(runtime.contains("EncryptedFileOutcomeFollowupStore"), "Android runtime must compose the encrypted durable follow-up store");
        check(runtime.contains("OutcomeFollowupRuntime"), "Android runtime must expose the shared privacy-owned follow-up runtime");

        System.out.println("AndroidOutcomeFollowupPersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
