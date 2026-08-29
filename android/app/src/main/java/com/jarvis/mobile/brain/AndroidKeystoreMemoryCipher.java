package com.jarvis.mobile.brain;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.jarvis.brain.MemoryCipher;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Android Keystore-backed AES-GCM cipher for private JARVIS durable brain state. */
public final class AndroidKeystoreMemoryCipher implements MemoryCipher {
    private static final String ALIAS = "jarvis.outcome.followups.v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final byte VERSION = 1;

    @Override
    public byte[] encrypt(byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plaintext == null ? new byte[0] : plaintext);
            ByteBuffer packed = ByteBuffer.allocate(2 + iv.length + encrypted.length);
            packed.put(VERSION).put((byte) iv.length).put(iv).put(encrypted);
            return packed.array();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt JARVIS follow-up state", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) {
        try {
            if (ciphertext == null || ciphertext.length < 3) throw new IllegalArgumentException("invalid ciphertext");
            ByteBuffer packed = ByteBuffer.wrap(ciphertext);
            if (packed.get() != VERSION) throw new IllegalArgumentException("unsupported ciphertext version");
            int ivLength = Byte.toUnsignedInt(packed.get());
            if (ivLength < 12 || ivLength > packed.remaining()) throw new IllegalArgumentException("invalid iv length");
            byte[] iv = new byte[ivLength];
            packed.get(iv);
            byte[] encrypted = new byte[packed.remaining()];
            packed.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt JARVIS follow-up state", e);
        }
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(ALIAS, null);
        if (existing instanceof SecretKey secretKey) return secretKey;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
