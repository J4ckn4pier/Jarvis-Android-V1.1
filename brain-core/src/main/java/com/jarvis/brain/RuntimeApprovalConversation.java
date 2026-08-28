package com.jarvis.brain;
import java.util.Locale;
/** Converts short approval/deferral/recovery replies into the exact pending BrainRuntime path. */
public final class RuntimeApprovalConversation{
 private final BrainRuntime runtime; public RuntimeApprovalConversation(BrainRuntime runtime){if(runtime==null)throw new IllegalArgumentException("runtime required");this.runtime=runtime;}
 public synchronized RuntimeSurfacePresentation handle(String utterance){String n=utterance==null?"":utterance.trim().toLowerCase(Locale.ROOT);if(runtime.hasPendingApproval()){if(isApproval(n))return RuntimeSurfacePresentation.from(runtime.approvePending());if(isDeferral(n))return cancel("Not yet.");}if(runtime.hasPendingRecovery()){if(isRetry(n))return RuntimeSurfacePresentation.from(runtime.retryPending());if(isDeferral(n))return cancel("Not yet.");}return RuntimeSurfacePresentation.from(runtime.handle(utterance));}
 public synchronized RuntimeSurfacePresentation approvePending(){return RuntimeSurfacePresentation.from(runtime.approvePending());}
 public synchronized RuntimeSurfacePresentation retryPending(){return RuntimeSurfacePresentation.from(runtime.retryPending());}
 public synchronized RuntimeSurfacePresentation cancelPending(){return cancel("Cancelled.");}
 public synchronized boolean hasPendingApproval(){return runtime.hasPendingApproval();} public synchronized boolean hasPendingRecovery(){return runtime.hasPendingRecovery();}
 private RuntimeSurfacePresentation cancel(String text){runtime.cancelPending();return new RuntimeSurfacePresentation(AssistantSurfaceState.IDLE,text,"Pending action cancelled",RuntimeSurfaceAction.NONE,RuntimeSurfaceAction.NONE);}
 private static boolean isApproval(String v){return v.equals("yes")||v.equals("yes please")||v.equals("do it")||v.equals("go ahead")||v.equals("send it")||v.equals("confirm");}
 private static boolean isRetry(String v){return v.equals("retry")||v.equals("try again")||v.equals("again");}
 private static boolean isDeferral(String v){return v.equals("no")||v.equals("not yet")||v.equals("cancel")||v.equals("never mind")||v.equals("nevermind")||v.equals("later");}
}
