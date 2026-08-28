package com.jarvis.brain;

import java.util.List;

public record DocumentAnalysis(String documentId, String summary, List<String> findings) {
    public DocumentAnalysis {
        documentId = require(documentId,"document id"); summary = require(summary,"summary"); findings = findings == null ? List.of() : List.copyOf(findings);
    }
    private static String require(String v,String label){String s=v==null?"":v.trim();if(s.isBlank())throw new IllegalArgumentException(label+" required");return s;}
}
