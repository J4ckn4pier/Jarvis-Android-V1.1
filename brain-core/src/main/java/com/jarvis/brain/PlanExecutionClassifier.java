package com.jarvis.brain;

/** Classifies validated plans from registry metadata. Mixed execution classes are not autonomous-research plans. */
public final class PlanExecutionClassifier {
 private final ToolRegistry tools;
 public PlanExecutionClassifier(ToolRegistry tools){if(tools==null)throw new IllegalArgumentException("tool registry required");this.tools=tools;}
 public boolean containsAutonomousResearch(Plan plan){if(plan==null||plan.steps().isEmpty())return false;for(PlanStep step:plan.steps()){ToolRegistry.RegisteredTool r=tools.resolve(step.tool()).orElse(null);if(r!=null&&r.spec().executionClass()==ToolExecutionClass.AUTONOMOUS_RESEARCH)return true;}return false;}
 public boolean isPureAutonomousResearch(Plan plan){if(plan==null||plan.steps().isEmpty())return false;for(PlanStep step:plan.steps()){ToolRegistry.RegisteredTool r=tools.resolve(step.tool()).orElse(null);if(r==null||r.spec().executionClass()!=ToolExecutionClass.AUTONOMOUS_RESEARCH)return false;}return true;}
}
