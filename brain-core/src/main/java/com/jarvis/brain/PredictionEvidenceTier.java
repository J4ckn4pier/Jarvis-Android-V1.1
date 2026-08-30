package com.jarvis.brain;

public enum PredictionEvidenceTier {
    INFERRED,
    CORROBORATED,
    TRUSTED;

    public boolean maySpeakProactively() {
        return this == CORROBORATED || this == TRUSTED;
    }
}
