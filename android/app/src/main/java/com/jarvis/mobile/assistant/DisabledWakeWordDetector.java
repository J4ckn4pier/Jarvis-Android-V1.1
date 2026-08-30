package com.jarvis.mobile.assistant;

import com.jarvis.brain.WakeWordModelDescriptor;

/** Truthful no-capture fallback used when no commercially approved local wake model is configured. */
final class DisabledWakeWordDetector implements WakeWordDetectorPort {
    private final String reason;

    DisabledWakeWordDetector(String reason) {
        this.reason = reason == null || reason.isBlank() ? "passive wake disabled" : reason;
    }

    @Override public WakeWordModelDescriptor modelDescriptor() {
        return new WakeWordModelDescriptor("disabled", "", "UNKNOWN", false, false);
    }

    @Override public boolean start(Runnable onWake) { return false; }
    @Override public void stop() { }
    @Override public boolean isRunning() { return false; }
    @Override public String status() { return reason; }
}
