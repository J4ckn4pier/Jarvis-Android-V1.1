package com.jarvis.brain;
/** UI-neutral popup state. YES only requests the existing approval path; it never executes a tool. */
public final class PopupOverlayController{
 private PopupOverlayState state=PopupOverlayState.hidden();
 public synchronized PopupOverlayState state(){return state;}
 public synchronized void show(String title,String detail){if(title==null||title.isBlank())throw new IllegalArgumentException("title required");state=new PopupOverlayState(true,title,detail,false);}
 public synchronized void dismiss(){state=PopupOverlayState.hidden();}
 public synchronized PopupOverlayOutcome onAction(PopupOverlayAction action){if(action==null)throw new IllegalArgumentException("action required");if(!state.visible())throw new IllegalStateException("overlay is not visible");return switch(action){case DETAILS->{state=new PopupOverlayState(true,state.title(),state.detail(),true);yield PopupOverlayOutcome.SHOW_DETAILS;}case LATER->{dismiss();yield PopupOverlayOutcome.DEFERRED;}case YES->{dismiss();yield PopupOverlayOutcome.APPROVAL_REQUESTED;}};}
}
