package com.jarvis.brain;
import java.time.*;
public final class EpisodeFollowupPolicyTest {
 public static void main(String[] args){
  Instant t=Instant.parse("2026-08-28T18:00:00Z"); RecommendationEpisode e=new RecommendationEpisode("x","movie","Arrival",t); EpisodeFollowupPolicy p=new EpisodeFollowupPolicy(Duration.ofMinutes(30));
  check(p.candidate(e,new EpisodeFollowupPolicy.Trigger(true,true,false,t),t.plusSeconds(3600)).isEmpty(),"presence cannot trigger without opt-in");
  check(p.candidate(e,new EpisodeFollowupPolicy.Trigger(false,false,false,t),t.plusSeconds(3600)).isEmpty(),"unacted recommendation gets no followup");
  check(p.candidate(e,new EpisodeFollowupPolicy.Trigger(true,false,false,t),t.plusSeconds(60)).isEmpty(),"minimum delay respected");
  PredictionCandidate c=p.candidate(e,new EpisodeFollowupPolicy.Trigger(true,true,true,t),t.plusSeconds(3600)).orElseThrow(); check(c.category()==PredictionCategory.RECOMMENDATION_FOLLOWUP,"typed followup category"); check(c.evidenceTier()==PredictionEvidenceTier.TRUSTED,"explicit episode trigger trusted"); check(c.message().contains("Arrival"),"specific episode subject retained");
  System.out.println("EpisodeFollowupPolicyTest passed");
 }
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
