package com.jarvis.brain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class ProviderAttemptBudgetTest {
    private static int checks;
    public static void main(String[] args) {
        malformedPreferredProviderFallsBackWithinExplicitBudget();
        budgetPreventsWalkingEntireProviderFleet();
        System.out.println("ProviderAttemptBudgetTest: " + checks + " assertions passed");
    }
    private static void malformedPreferredProviderFallsBackWithinExplicitBudget() {
        int[] localCalls={0}; int[] fallbackCalls={0};
        ReasoningProvider local=provider("local",()->{localCalls[0]++;throw new RuntimeException("malformed plan");});
        ReasoningProvider fallback=provider("fallback",()->{fallbackCalls[0]++;return new ReasoningResult("fallback","clarified safely",null);});
        PolicyProviderRouter router=new PolicyProviderRouter(
                List.of(new ProviderRoute(local,ProviderTier.FREE_LOCAL,0),new ProviderRoute(fallback,ProviderTier.FREE_EXTERNAL,0)),
                false,2,Duration.ofMinutes(5),fixedClock(),2);
        ReasoningResult result=router.reason(new ReasoningRequest("do it","",List.of()));
        check(result.providerId().equals("fallback"),"malformed local result should fall back to next permitted provider");
        check(localCalls[0]==1&&fallbackCalls[0]==1,"two-attempt budget should permit exactly local plus one fallback");
    }
    private static void budgetPreventsWalkingEntireProviderFleet() {
        int[] calls={0};
        ReasoningProvider p1=provider("p1",()->{calls[0]++;throw new RuntimeException("bad1");});
        ReasoningProvider p2=provider("p2",()->{calls[0]++;throw new RuntimeException("bad2");});
        ReasoningProvider p3=provider("p3",()->{calls[0]++;return new ReasoningResult("p3","would have answered",null);});
        PolicyProviderRouter router=new PolicyProviderRouter(
                List.of(new ProviderRoute(p1,ProviderTier.FREE_LOCAL,0),new ProviderRoute(p2,ProviderTier.FREE_EXTERNAL,0),new ProviderRoute(p3,ProviderTier.FREE_EXTERNAL,1)),
                false,3,Duration.ZERO,fixedClock(),2);
        ReasoningResult result=router.reason(new ReasoningRequest("ambiguous","",List.of()));
        check(calls[0]==2,"router must stop after explicit per-request attempt budget");
        check(result.providerId().equals("none"),"exhausted budget should return safe non-executable fallback rather than silently trying provider three");
    }
    private interface Call { ReasoningResult run(); }
    private static ReasoningProvider provider(String id,Call call){return new ReasoningProvider(){@Override public String id(){return id;}@Override public boolean available(){return true;}@Override public ReasoningResult reason(ReasoningRequest request){return call.run();}};}
    private static Clock fixedClock(){return Clock.fixed(Instant.parse("2026-08-28T03:35:00Z"),ZoneOffset.UTC);}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
