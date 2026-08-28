package com.jarvis.brain;

public record MessageDraft(String id, String recipient, String body) {
    public MessageDraft {
        id = require(id, "id");
        recipient = require(recipient, "recipient");
        body = body == null ? "" : body.trim();
    }
    private static String require(String v, String label) {
        String s = v == null ? "" : v.trim();
        if (s.isBlank()) throw new IllegalArgumentException(label + " required");
        return s;
    }
}
