package com.jarvis.brain;

public final class AttentionController {
    public enum State { SLEEPING, LISTENING, THINKING, SPEAKING, OPEN_IDLE }

    private final boolean echoCancellationAvailable;
    private State state = State.SLEEPING;

    public AttentionController(boolean echoCancellationAvailable) {
        this.echoCancellationAvailable = echoCancellationAvailable;
    }

    public State state() { return state; }

    public void onWakeDetected() {
        if (state == State.SLEEPING || state == State.OPEN_IDLE) state = State.LISTENING;
    }

    public void onSpeechStarted() {
        if (state == State.OPEN_IDLE || state == State.LISTENING) {
            state = State.LISTENING;
        } else if (state == State.SPEAKING && echoCancellationAvailable) {
            state = State.LISTENING;
        }
    }

    public void onSpeechCommitted() {
        if (state == State.LISTENING) state = State.THINKING;
    }

    public void onResponseSpeaking() {
        if (state == State.THINKING) state = State.SPEAKING;
    }

    public void onResponseFinished() {
        if (state == State.SPEAKING) state = State.OPEN_IDLE;
    }

    public void onIdleTimeout() {
        if (state == State.OPEN_IDLE || state == State.LISTENING) state = State.SLEEPING;
    }

    public void dismiss() { state = State.SLEEPING; }
}
