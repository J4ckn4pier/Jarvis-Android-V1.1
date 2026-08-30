package com.jarvis.brain;

/** Deterministic interruption task-control state. Never executes tools or grants approval. */
public final class ExecutiveTaskController {
    private String currentGoal;
    private String parallelGoal = "";
    private String suspendedGoal = "";
    private String context = "";
    public ExecutiveTaskController(String currentGoal) { this.currentGoal=clean(currentGoal); if(this.currentGoal.isBlank())throw new IllegalArgumentException("current goal required"); }
    public synchronized TaskControlResult apply(InterruptionDecision decision,String incomingUtterance){
        if(decision==null)throw new IllegalArgumentException("interruption decision required"); String incoming=clean(incomingUtterance);if(incoming.isBlank())throw new IllegalArgumentException("incoming utterance required");
        switch(decision){
            case RESTART_CURRENT->{currentGoal=incoming;parallelGoal="";suspendedGoal="";context="";}
            case INCORPORATE_CONTEXT->context=append(context,incoming);
            case DO_BOTH->parallelGoal=incoming;
            case SWITCH->{suspendedGoal=currentGoal;currentGoal=incoming;context="";}
            case ASK->{return new TaskControlResult(decision,true);}
        }
        return new TaskControlResult(decision,false);
    }
    public synchronized String completeCurrentGoal(){
        if(!suspendedGoal.isBlank()){currentGoal=suspendedGoal;suspendedGoal="";context="";return currentGoal;}
        if(!parallelGoal.isBlank()){currentGoal=parallelGoal;parallelGoal="";context="";return currentGoal;}
        currentGoal="";context="";return currentGoal;
    }
    public synchronized String completeParallelGoal(){parallelGoal="";return currentGoal;}
    public synchronized String currentGoal(){return currentGoal;} public synchronized String parallelGoal(){return parallelGoal;} public synchronized String queuedGoal(){return parallelGoal;} public synchronized String suspendedGoal(){return suspendedGoal;} public synchronized String context(){return context;}
    private static String append(String existing,String addition){return existing==null||existing.isBlank()?addition:existing+"\n"+addition;} private static String clean(String value){return value==null?"":value.trim();}
}
