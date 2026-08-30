package com.jarvis.brain;
public final class PopupOverlayControllerTest {
 private static int checks;
 public static void main(String[]a){
  PopupOverlayController p=new PopupOverlayController();
  p.show("Reservation available at 5 PM","Confirm booking?");
  check(p.state().visible(),"visible");check(!p.state().expanded(),"new popup starts collapsed");check(p.state().title().contains("Reservation"),"title");
  check(p.onAction(PopupOverlayAction.DETAILS)==PopupOverlayOutcome.SHOW_DETAILS,"details");check(p.state().visible(),"details does not dismiss");check(p.state().expanded(),"details expansion is backend-owned state");
  check(p.onAction(PopupOverlayAction.LATER)==PopupOverlayOutcome.DEFERRED,"later");check(!p.state().visible(),"later dismisses");check(!p.state().expanded(),"dismiss resets expansion");
  p.show("Send message","Send to Mom?");check(!p.state().expanded(),"subsequent popup starts collapsed");check(p.onAction(PopupOverlayAction.YES)==PopupOverlayOutcome.APPROVAL_REQUESTED,"yes requests approval rather than executing");check(!p.state().visible(),"yes dismisses");
  PredictionCandidate candidate=new PredictionCandidate("Leave in 10 minutes to make your appointment.",0.95,0.95,0.95,PredictionEvidenceTier.TRUSTED,PredictionCategory.IMMINENT_COMMITMENT);
  check(p.surface(new ProactiveIntervention(InterventionMode.NOTIFY,candidate,"useful notification")),"notify intervention surfaces popup");
  check(p.state().visible(),"proactive notification is visible");check(p.state().detail().contains("Leave in 10 minutes"),"candidate message reaches popup detail");
  p.dismiss();
  check(!p.surface(ProactiveIntervention.silent(candidate,"below threshold")),"silent intervention does not surface popup");check(!p.state().visible(),"silent intervention leaves popup hidden");
  check(!p.surface(new ProactiveIntervention(InterventionMode.SPEAK,candidate,"spoken instead")),"spoken intervention is not duplicated as popup");check(!p.state().visible(),"spoken intervention leaves popup hidden");
  System.out.println("PopupOverlayControllerTest: "+checks+" assertions passed");
 }
 private static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
}
