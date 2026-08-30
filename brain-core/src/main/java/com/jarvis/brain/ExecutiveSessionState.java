package com.jarvis.brain;

import java.util.Optional;

/** Persistent per-conversation executive task state, independent of one reasoning call. */
public final class ExecutiveSessionState {
    private ExecutiveTaskController tasks;
    public synchronized void begin(String goal){ if(goal==null||goal.isBlank()) throw new IllegalArgumentException("goal required"); tasks=new ExecutiveTaskController(goal); }
    public synchronized boolean active(){ return tasks!=null && !tasks.currentGoal().isBlank(); }
    public synchronized Optional<ExecutiveTaskController> tasks(){ return Optional.ofNullable(tasks); }
    public synchronized TaskControlResult interrupt(InterruptionDecision decision,String utterance){ if(tasks==null) throw new IllegalStateException("no active executive goal"); return tasks.apply(decision,utterance); }
    public synchronized String completeCurrent(){ if(tasks==null)return ""; String next=tasks.completeCurrentGoal(); if(next.isBlank())tasks=null; return next; }
    public synchronized void clear(){ tasks=null; }
}
