package com.jarvis.mobile.assistant;

import com.jarvis.brain.WakeWordModelDescriptor;

/** Local passive-wake boundary. Implementations must not stream idle microphone audio to cloud services. */
interface WakeWordDetectorPort {
    WakeWordModelDescriptor modelDescriptor();
    boolean start(Runnable onWake);
    void stop();
    boolean isRunning();
    String status();
}
