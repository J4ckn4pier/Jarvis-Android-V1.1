package com.jarvis.brain;
public final class ExecutiveSessionStateTest {
 public static void main(String[] args){
  ExecutiveSessionState s=new ExecutiveSessionState(); check(!s.active(),"starts idle"); s.begin("find Chinese dinner");
  s.interrupt(InterruptionDecision.RESTART_CURRENT,"find Italian dinner instead"); check(s.tasks().orElseThrow().currentGoal().contains("Italian"),"correction replaces current goal");
  s.interrupt(InterruptionDecision.DO_BOTH,"check tomorrow weather"); check(s.tasks().orElseThrow().parallelGoal().contains("weather"),"safe parallel goal retained");
  s.interrupt(InterruptionDecision.SWITCH,"answer urgent doorbell"); check(s.tasks().orElseThrow().suspendedGoal().contains("Italian"),"switch suspends prior goal"); check(s.tasks().orElseThrow().parallelGoal().contains("weather"),"switch must not discard independent parallel work");
  check(s.completeCurrent().contains("Italian"),"completion resumes suspended goal"); check(s.completeCurrent().contains("weather"),"then promotes parallel work"); check(s.completeCurrent().isBlank()&&!s.active(),"eventually idle");
  System.out.println("ExecutiveSessionStateTest passed");
 }
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
