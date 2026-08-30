package com.jarvis.brain;
/** UI-neutral popup state. YES only requests the existing approval path; it never executes a tool. */
public final class PopupOverlayController{
 private PopupOverlayState state=PopupOverlayState.hidden();
 public synchronized PopupOverlayState state(){return state;}
 public synchronized void show(String title,String detail){if(title==null||title.isBlank())throw new IllegalArgumentException("title required");state=new PopupOverlayState(true,title,detail,false);}
 /** Projects notification-mode proactive interventions into the popup surface. SILENT/SPEAK are intentionally not duplicated here. */
 public synchronized boolean surface(ProactiveIntervention intervention){
  if(intervention==null||intervention.mode()!=InterventionMode.NOTIFY||intervention.candidate()==null)return false;
  String message=intervention.candidate().message()==null?"":intervention.candidate().message().trim();
  if(message.isBlank())return false;
  show(titleFor(intervention.candidate().category()),message);
  return true;
 }
 public synchronized void dismiss(){state=PopupOverlayState.hidden();}
 public synchronized PopupOverlayOutcome onAction(PopupOverlayAction action){if(action==null)throw new IllegalArgumentException("action required");if(!state.visible())throw new IllegalStateException("overlay is not visible");return switch(action){case DETAILS->{state=new PopupOverlayState(true,state.title(),state.detail(),true);yield PopupOverlayOutcome.SHOW_DETAILS;}case LATER->{dismiss();yield PopupOverlayOutcome.DEFERRED;}case YES->{dismiss();yield PopupOverlayOutcome.APPROVAL_REQUESTED;}};}
 private static String titleFor(PredictionCategory category){if(category==null)return "JARVIS";return switch(category){case TIMER->"Timer";case REMINDER->"Reminder";case CALENDAR_CONFLICT->"Calendar conflict";case IMMINENT_COMMITMENT->"Upcoming commitment";case RECOMMENDATION_FOLLOWUP->"Follow-up";case GENERAL->"JARVIS";};}
}
