package com.jarvis.brain;
public final class InterruptionCoordinatorTest{
 public static void main(String[]a){ExecutiveSessionState s=new ExecutiveSessionState();s.begin("find Chinese");InterruptionCoordinator c=new InterruptionCoordinator(new GoalInterruptionPolicy(),s);
  TaskControlResult r=c.onInterruption(new InterruptionContext("find Chinese","actually Italian instead",.9,true,true,false,.2));check(r.action()==InterruptionDecision.RESTART_CURRENT,"correction reroutes");check(s.tasks().orElseThrow().currentGoal().contains("Italian"),"persistent state updated");
  String before=s.tasks().orElseThrow().currentGoal();r=c.onInterruption(new InterruptionContext(before,"send Mom this",.1,false,true,true,.2));check(r.action()==InterruptionDecision.ASK&&r.requiresUserDecision(),"consequential boundary asks");check(s.tasks().orElseThrow().currentGoal().equals(before),"ASK cannot mutate task");System.out.println("InterruptionCoordinatorTest passed");}
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
