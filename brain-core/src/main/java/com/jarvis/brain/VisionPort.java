package com.jarvis.brain;

/** Camera/OCR/vision implementation boundary. */
@FunctionalInterface
public interface VisionPort {
    VisionResult analyze(String captureReference);
}
