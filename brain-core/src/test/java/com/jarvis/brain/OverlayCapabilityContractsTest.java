package com.jarvis.brain;

import java.util.List;
import java.util.Map;

public final class OverlayCapabilityContractsTest {
    private static int checks;
    public static void main(String[] args) {
        assistantSurfaceProjectsAttentionWithoutInventingState();
        draftsAreEditableButSendingStaysOutsideDraftStore();
        visionProducesTypedTaskSuggestions();
        appLaunchAndDocumentAnalysisAreExplicitPorts();
        callerScreeningHasExplicitNonAnsweringBoundary();
        System.out.println("OverlayCapabilityContractsTest: " + checks + " assertions passed");
    }

    private static void assistantSurfaceProjectsAttentionWithoutInventingState() {
        AttentionController attention = new AttentionController(true);
        AssistantSurfaceController surface = new AssistantSurfaceController(attention);
        check(surface.state() == AssistantSurfaceState.IDLE, "sleeping projects idle");
        attention.onWakeDetected(); check(surface.state() == AssistantSurfaceState.LISTENING, "wake projects listening");
        attention.onSpeechCommitted(); check(surface.state() == AssistantSurfaceState.THINKING, "commit projects thinking");
        attention.onResponseSpeaking(); check(surface.state() == AssistantSurfaceState.RESPONDING, "speech projects responding");
        surface.markActionDone("Reservation confirmed"); check(surface.state() == AssistantSurfaceState.ACTION_DONE, "done is explicit transient outcome");
        check(surface.detail().contains("Reservation confirmed"), "action detail retained");
    }

    private static void draftsAreEditableButSendingStaysOutsideDraftStore() {
        DraftMessageStore drafts = new DraftMessageStore();
        drafts.save(new MessageDraft("d1","Mom","I’m on my way"));
        drafts.save(new MessageDraft("d1","Mom","I’ll be home soon"));
        check(drafts.get("d1").orElseThrow().body().contains("home soon"), "draft edit persists");
        check(drafts.remove("d1"), "draft cancel/remove works");
    }

    private static void visionProducesTypedTaskSuggestions() {
        VisionResult result = new VisionResult("note-1", "handwritten note",
                List.of("Buy milk", "Call dentist", "Submit form"), Map.of("confidence","0.92"));
        check(result.suggestedTasks().size() == 3, "vision task suggestions typed");
        check(result.summary().contains("handwritten"), "vision summary retained");
    }

    private static void appLaunchAndDocumentAnalysisAreExplicitPorts() {
        AppLauncherPort launcher = new AppLauncherPort() {
            public ToolResult launch(String appName){ return ToolResult.success("Opened " + appName); }
        };
        DocumentAnalysisPort docs = new DocumentAnalysisPort() {
            public DocumentAnalysis analyze(DocumentReference document){ return new DocumentAnalysis(document.id(), "Lease renews Dec 3", List.of("Notice: 30 days")); }
        };
        check(launcher.launch("Maps").status() == ToolResult.Status.SUCCESS, "launcher port returns real outcome");
        check(docs.analyze(new DocumentReference("lease","content://lease.pdf","application/pdf")).summary().contains("Dec 3"), "document analysis typed");
    }

    private static void callerScreeningHasExplicitNonAnsweringBoundary() {
        CallScreeningPort screening = incoming -> new CallerScreeningResult(incoming.id(), CallerScreeningResult.Decision.ALLOW, "Known contact");
        CallerScreeningResult result = screening.screen(new IncomingCaller("c1","Dave R.","+15550100"));
        check(result.decision() == CallerScreeningResult.Decision.ALLOW, "screening decision returned");
    }

    private static void check(boolean value, String label){ checks++; if(!value) throw new AssertionError(label); }
}
