package com.jarvis.brain;

public record DocumentReference(String id, String uri, String mimeType) {
    public DocumentReference {
        id = require(id,"id"); uri = require(uri,"uri"); mimeType = mimeType == null ? "application/octet-stream" : mimeType.trim();
    }
    private static String require(String v,String label){String s=v==null?"":v.trim();if(s.isBlank())throw new IllegalArgumentException(label+" required");return s;}
}
