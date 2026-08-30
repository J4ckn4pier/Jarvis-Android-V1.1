package com.jarvis.brain;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/** Portable AES-GCM codec. Android should supply the key from Android Keystore. */
public final class AesGcmMemoryCipher implements MemoryCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte VERSION = 1;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmMemoryCipher(byte[] keyBytes) {
        if (keyBytes == null || !(keyBytes.length == 16 || keyBytes.length == 24 || keyBytes.length == 32)) {
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes");
        }
        this.key = new SecretKeySpec(Arrays.copyOf(keyBytes, keyBytes.length), "AES");
    }

    @Override
    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] body = cipher.doFinal(plaintext == null ? new byte[0] : plaintext);
            byte[] out = new byte[1 + NONCE_BYTES + body.length];
            out[0] = VERSION;
            System.arraycopy(nonce, 0, out, 1, NONCE_BYTES);
            System.arraycopy(body, 0, out, 1 + NONCE_BYTES, body.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt memory", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length <= 1 + NONCE_BYTES || ciphertext[0] != VERSION) {
            throw new IllegalArgumentException("Invalid encrypted memory payload");
        }
        try {
            byte[] nonce = Arrays.copyOfRange(ciphertext, 1, 1 + NONCE_BYTES);
            byte[] body = Arrays.copyOfRange(ciphertext, 1 + NONCE_BYTES, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(body);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt memory", e);
        }
    }
}
