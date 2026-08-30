package com.jarvis.brain;

public interface MemoryCipher {
    byte[] encrypt(byte[] plaintext);
    byte[] decrypt(byte[] ciphertext);
}
