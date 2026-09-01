package com.jarvis.mobile.assistant;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

/**
 * Lightweight local speech-energy monitor used only while JARVIS TTS is speaking.
 * It fails closed unless Android exposes AcousticEchoCanceler for the capture session,
 * avoiding the unsafe behavior of treating JARVIS's own speaker output as user barge-in.
 */
final class AndroidAecBargeInMonitor {
    private static final int SAMPLE_RATE_HZ = 16_000;
    private static final int ENERGY_THRESHOLD = 1_200;
    private static final int REQUIRED_HOT_FRAMES = 2;

    private final Context context;
    private final Object lock = new Object();
    private volatile boolean running;
    private AudioRecord audioRecord;
    private AcousticEchoCanceler echoCanceler;
    private Thread worker;

    AndroidAecBargeInMonitor(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean isSupported() {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && isAecAvailableSafely();
    }

    boolean start(Runnable onBargeIn) {
        if (onBargeIn == null || !isSupported()) return false;
        synchronized (lock) {
            stopLocked();
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            int minimum = minimumBufferSizeSafely();
            if (minimum <= 0) return false;
            int bufferBytes = Math.max(minimum, SAMPLE_RATE_HZ / 5 * 2);
            AudioRecord candidate;
            try {
                candidate = new AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE_HZ)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferBytes)
                        .build();
            } catch (SecurityException permissionRevoked) {
                return false;
            } catch (RuntimeException unavailable) {
                return false;
            }
            if (candidate.getState() != AudioRecord.STATE_INITIALIZED) {
                candidate.release();
                return false;
            }

            AcousticEchoCanceler candidateAec;
            try {
                candidateAec = AcousticEchoCanceler.create(candidate.getAudioSessionId());
                if (candidateAec == null) {
                    candidate.release();
                    return false;
                }
                candidateAec.setEnabled(true);
                if (!candidateAec.getEnabled()) {
                    candidateAec.release();
                    candidate.release();
                    return false;
                }
            } catch (RuntimeException unavailable) {
                candidate.release();
                return false;
            }

            audioRecord = candidate;
            echoCanceler = candidateAec;
            running = true;
            worker = new Thread(() -> monitorLoop(onBargeIn), "jarvis-aec-barge-in");
            worker.setDaemon(true);
            try {
                audioRecord.startRecording();
                worker.start();
                return true;
            } catch (SecurityException permissionRevoked) {
                stopLocked();
                return false;
            } catch (RuntimeException startFailure) {
                stopLocked();
                return false;
            }
        }
    }

    void stop() {
        synchronized (lock) {
            stopLocked();
        }
    }

    private boolean isAecAvailableSafely() {
        try {
            return AcousticEchoCanceler.isAvailable();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private int minimumBufferSizeSafely() {
        try {
            return AudioRecord.getMinBufferSize(
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
        } catch (RuntimeException unavailable) {
            return AudioRecord.ERROR;
        }
    }

    private void monitorLoop(Runnable onBargeIn) {
        short[] buffer = new short[Math.max(320, SAMPLE_RATE_HZ / 20)];
        int hotFrames = 0;
        boolean detected = false;
        try {
            while (running) {
                AudioRecord recorder = audioRecord;
                if (recorder == null) break;
                int read = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (read <= 0) continue;
                if (averageAbsoluteAmplitude(buffer, read) >= ENERGY_THRESHOLD) {
                    hotFrames++;
                    if (hotFrames >= REQUIRED_HOT_FRAMES) {
                        detected = true;
                        running = false;
                        break;
                    }
                } else {
                    hotFrames = 0;
                }
            }
        } catch (SecurityException permissionRevoked) {
            // Runtime microphone permission can disappear while the app is active; fail closed.
        } catch (RuntimeException ignored) {
            // Capture failure simply disables hands-free barge-in; the normal conversation loop remains usable.
        } finally {
            synchronized (lock) {
                releaseCaptureLocked();
            }
        }
        if (detected) onBargeIn.run();
    }

    private static int averageAbsoluteAmplitude(short[] samples, int length) {
        long total = 0L;
        for (int i = 0; i < length; i++) total += Math.abs((int) samples[i]);
        return length == 0 ? 0 : (int) (total / length);
    }

    private void stopLocked() {
        running = false;
        AudioRecord recorder = audioRecord;
        if (recorder != null) {
            try {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
            } catch (RuntimeException ignored) { }
        }
        releaseCaptureLocked();
        worker = null;
    }

    private void releaseCaptureLocked() {
        AcousticEchoCanceler aec = echoCanceler;
        echoCanceler = null;
        if (aec != null) {
            try { aec.release(); } catch (RuntimeException ignored) { }
        }
        AudioRecord recorder = audioRecord;
        audioRecord = null;
        if (recorder != null) {
            try { recorder.release(); } catch (RuntimeException ignored) { }
        }
    }
}
