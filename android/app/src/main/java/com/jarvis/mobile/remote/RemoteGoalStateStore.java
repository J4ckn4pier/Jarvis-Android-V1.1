package com.jarvis.mobile.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Persists reconnect state and protects the remote credential with Android Keystore AES-GCM. */
public final class RemoteGoalStateStore {
    private static final String PREFS = "jarvis_remote_goal_state";
    private static final String PROJECT_ID = "project_id";
    private static final String EVENT_ID = "event_id";
    private static final String CONNECTION = "connection_ciphertext";
    private static final String KEY_ALIAS = "jarvis.remote.goal.connection.v1";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final byte VERSION = 1;

    private final SharedPreferences preferences;

    public RemoteGoalStateStore(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public State load() {
        return new State(preferences.getString(PROJECT_ID, null), preferences.getString(EVENT_ID, null));
    }

    public void saveConnection(String baseUrl, String token) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("base_url is required");
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Bearer token is required");
        String payload = baseUrl + "\n" + token;
        preferences.edit().putString(CONNECTION, encrypt(payload)).apply();
    }

    public Connection loadConnection() {
        String ciphertext = preferences.getString(CONNECTION, null);
        if (ciphertext == null || ciphertext.isBlank()) return null;
        try {
            String payload = decrypt(ciphertext);
            int separator = payload.indexOf('\n');
            if (separator <= 0 || separator == payload.length() - 1) return null;
            return new Connection(payload.substring(0, separator), payload.substring(separator + 1));
        } catch (RuntimeException invalidOrUnrecoverable) {
            return null;
        }
    }

    public void clearConnection() {
        preferences.edit().remove(CONNECTION).apply();
    }

    public void saveProject(String projectId) {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("project_id is required");
        preferences.edit().putString(PROJECT_ID, projectId).remove(EVENT_ID).apply();
    }

    public void saveCursor(String projectId, String eventId) {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("project_id is required");
        SharedPreferences.Editor editor = preferences.edit().putString(PROJECT_ID, projectId);
        if (eventId == null) editor.remove(EVENT_ID); else editor.putString(EVENT_ID, eventId);
        editor.apply();
    }

    public void clearProject() {
        preferences.edit().remove(PROJECT_ID).remove(EVENT_ID).apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    private static String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer packed = ByteBuffer.allocate(2 + iv.length + encrypted.length);
            packed.put(VERSION).put((byte) iv.length).put(iv).put(encrypted);
            return Base64.encodeToString(packed.array(), Base64.NO_WRAP);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to protect remote JARVIS connection", failure);
        }
    }

    private static String decrypt(String packedBase64) {
        try {
            byte[] bytes = Base64.decode(packedBase64, Base64.NO_WRAP);
            ByteBuffer packed = ByteBuffer.wrap(bytes);
            if (packed.remaining() < 3 || packed.get() != VERSION) throw new IllegalArgumentException("unsupported ciphertext");
            int ivLength = Byte.toUnsignedInt(packed.get());
            if (ivLength < 12 || ivLength > packed.remaining()) throw new IllegalArgumentException("invalid iv");
            byte[] iv = new byte[ivLength];
            packed.get(iv);
            byte[] encrypted = new byte[packed.remaining()];
            packed.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to restore remote JARVIS connection", failure);
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey secretKey) return secretKey;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    public record Connection(String baseUrl, String token) {}
    public record State(String projectId, String eventId) {
        public boolean hasProject() { return projectId != null && !projectId.isBlank(); }
    }
}
