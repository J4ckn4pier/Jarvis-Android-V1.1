package com.jarvis.brain;

public record IncomingCaller(String id, String displayName, String number) {
    public IncomingCaller {
        id = require(id,"id"); displayName = displayName == null ? "Unknown caller" : displayName.trim(); number = number == null ? "" : number.trim();
    }
    private static String require(String v,String label){String s=v==null?"":v.trim();if(s.isBlank())throw new IllegalArgumentException(label+" required");return s;}
}
