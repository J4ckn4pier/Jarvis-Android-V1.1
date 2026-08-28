package com.jarvis.brain;
public final class PopupOverlayControllerTest {
 private static int checks;
 public static void main(String[]a){PopupOverlayController p=new PopupOverlayController();p.show("Reservation available at 5 PM","Confirm booking?");check(p.state().visible(),"visible");check(p.state().title().contains("Reservation"),"title");check(p.onAction(PopupOverlayAction.DETAILS)==PopupOverlayOutcome.SHOW_DETAILS,"details");check(p.state().visible(),"details does not dismiss");check(p.onAction(PopupOverlayAction.LATER)==PopupOverlayOutcome.DEFERRED,"later");check(!p.state().visible(),"later dismisses");p.show("Send message","Send to Mom?");check(p.onAction(PopupOverlayAction.YES)==PopupOverlayOutcome.APPROVAL_REQUESTED,"yes requests approval rather than executing");check(!p.state().visible(),"yes dismisses");System.out.println("PopupOverlayControllerTest: "+checks+" assertions passed");}
 private static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
}
