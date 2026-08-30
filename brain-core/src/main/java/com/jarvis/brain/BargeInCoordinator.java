package com.jarvis.brain;

/**
 * Composes audio-attention barge-in with deterministic executive interruption policy.
 * Runtime TTS cancellation belongs to the device adapter; the brain contract requires
 * AttentionController to leave SPEAKING for LISTENING before task state is changed.
 */
public final class BargeInCoordinator {
    private final AttentionController attention;
    private final GoalInterruptionPolicy policy;
    private final ExecutiveTaskController tasks;

    public BargeInCoordinator(AttentionController attention,
                              GoalInterruptionPolicy policy,
                              ExecutiveTaskController tasks) {
        if (attention == null) throw new IllegalArgumentException("attention controller required");
        if (policy == null) throw new IllegalArgumentException("interruption policy required");
        if (tasks == null) throw new IllegalArgumentException("task controller required");
        this.attention = attention;
        this.policy = policy;
        this.tasks = tasks;
    }

    public synchronized TaskControlResult onUserBargeIn(InterruptionContext context) {
        if (context == null) throw new IllegalArgumentException("interruption context required");
        attention.onSpeechStarted();
        if (attention.state() == AttentionController.State.SPEAKING) {
            throw new IllegalStateException("barge-in unavailable while assistant speech still owns the audio channel");
        }
        InterruptionDecision decision = policy.decide(context);
        return tasks.apply(decision, context.incomingUtterance());
    }
}
