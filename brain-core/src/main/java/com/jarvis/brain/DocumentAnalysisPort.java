package com.jarvis.brain;

@FunctionalInterface
public interface DocumentAnalysisPort {
    DocumentAnalysis analyze(DocumentReference document);
}
