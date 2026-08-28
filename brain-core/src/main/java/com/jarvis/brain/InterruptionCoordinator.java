package com.jarvis.brain;

/** Composes legible interruption policy with persistent executive session state. */
public final class InterruptionCoordinator {
 private final GoalInterruptionPolicy policy; private final ExecutiveSessionState session;
 public InterruptionCoordinator(GoalInterruptionPolicy policy,ExecutiveSessionState session){this.policy=policy==null?new GoalInterruptionPolicy():policy;this.session=session==null?new ExecutiveSessionState():session;}
 public TaskControlResult onInterruption(InterruptionContext context){if(!session.active())throw new IllegalStateException("no active executive task");InterruptionDecision d=policy.decide(context);return session.interrupt(d,context.incomingUtterance());}
 public ExecutiveSessionState session(){return session;}
}
