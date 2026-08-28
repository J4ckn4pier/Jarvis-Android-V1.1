package com.jarvis.brain;

public record CallerScreeningResult(String callerId, Decision decision, String reason) {
    public enum Decision { ALLOW, SILENCE, REJECT }
    public CallerScreeningResult {
        if(callerId==null||callerId.isBlank()) throw new IllegalArgumentException("caller id required");
        if(decision==null) throw new IllegalArgumentException("decision required");
        reason=reason==null?"":reason.trim();
    }
}
