package com.jarvis.mobile.assistant;

import android.content.Context;
import android.util.Log;

import com.jarvis.brain.CommercialWakeWordPolicy;
import com.jarvis.brain.WakeWordArtifactVerifier;
import com.jarvis.brain.WakeWordModelDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Properties;

/** App-private loader for a future commercially approved wake model artifact. */
final class AndroidWakeWordModelStore {
    private static final String TAG = "JARVIS_PASSIVE_WAKE";
    private static final String MODEL_FILE = "wake-model.bin";
    private static final String METADATA_FILE = "wake-model.properties";

    record ApprovedArtifact(Path modelPath, WakeWordModelDescriptor descriptor) { }

    private final Path directory;
    private final WakeWordArtifactVerifier verifier = new WakeWordArtifactVerifier(new CommercialWakeWordPolicy());

    AndroidWakeWordModelStore(Context context) {
        directory = context.getNoBackupFilesDir().toPath().resolve("jarvis").resolve("wake-word");
    }

    Optional<ApprovedArtifact> loadApproved() {
        Path model = directory.resolve(MODEL_FILE);
        Path metadata = directory.resolve(METADATA_FILE);
        if (!Files.isRegularFile(model) || !Files.isRegularFile(metadata)) return Optional.empty();
        try {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(metadata)) { properties.load(in); }

            WakeWordModelDescriptor descriptor = new WakeWordModelDescriptor(
                    properties.getProperty("identifier", ""),
                    properties.getProperty("sha256", ""),
                    properties.getProperty("license", ""),
                    Boolean.parseBoolean(properties.getProperty("commercialRedistributionAllowed", "false")),
                    Boolean.parseBoolean(properties.getProperty("trainingDataProvenanceVerified", "false")));
            String calculated = sha256(model);
            CommercialWakeWordPolicy.Decision decision = verifier.verify(descriptor, calculated);
            if (!decision.approved()) {
                Log.w(TAG, "Ignoring wake model: " + decision.reason());
                return Optional.empty();
            }
            return Optional.of(new ApprovedArtifact(model, descriptor));
        } catch (IOException | NoSuchAlgorithmException failure) {
            Log.w(TAG, "Unable to validate wake model artifact", failure);
            return Optional.empty();
        }
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(out);
    }
}
