package com.jarvis.brain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class SemanticReflexIntegrationTest {
    private static int checks;

    public static void main(String[] args) {
        paraphrasedDialerRequestAvoidsLiteralAppNameFailure();
        naturalCalendarQuestionRoutesOffline();
        naturalNavigationRequestRoutesOffline();
        torchParaphraseRoutesOffline();
        genericAppOpenRoutesOffline();
        timerRoutesOffline();
        alarmRoutesOffline();
        volumeRoutesOffline();
        mediaControlRoutesOffline();
        webSearchRoutesOffline();
        dinnerParaphraseBuildsDiscoveryPlan();
        descriptiveFoodDiscoveryDoesNotBuildDiscoveryPlan();
        ambiguousRequestStillFallsThroughToReasoning();
        System.out.println("SemanticReflexIntegrationTest: " + checks + " assertions passed");
    }

    private static AssistantCore core(int[] providerCalls) {
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T02:10:00Z"), ZoneOffset.UTC);
        ReasoningRouter provider = request -> { providerCalls[0]++; return new ReasoningResult("local", "I can reason about that.", null); };
        AssistantCore core = new AssistantCore(BrainEngine.createDefault(clock), provider, ToolRegistry.standard());
        core.handle("Hey Jarvis");
        return core;
    }

    private static void paraphrasedDialerRequestAvoidsLiteralAppNameFailure() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("open whatever I use to make a phone call");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN, "paraphrased calling request should become an action plan");
        check(r.plan().steps().get(0).tool().equals("open_dialer"), "calling paraphrase should resolve dialer capability");
        check(calls[0] == 0, "obvious local capability should not spend a model call");
    }

    private static void naturalCalendarQuestionRoutesOffline() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("what does my day look like tomorrow");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN && r.plan().steps().get(0).tool().equals("calendar_query"), "natural agenda question should route calendar query");
        check(r.plan().steps().get(0).arguments().get("when").equals("tomorrow"), "tomorrow should be retained as calendar time");
        check(calls[0] == 0, "calendar paraphrase should resolve deterministically");
    }

    private static void naturalNavigationRequestRoutesOffline() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("get me to the nearest gas station");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN && r.plan().steps().get(0).tool().equals("navigate"), "natural get-me-to phrasing should route navigation");
        check(r.plan().steps().get(0).arguments().get("destination").toLowerCase().contains("gas station"), "navigation should retain destination semantics");
        check(calls[0] == 0, "navigation paraphrase should not require reasoning provider");
    }

    private static void torchParaphraseRoutesOffline() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("kill the torch");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN && r.plan().steps().get(0).tool().equals("set_flashlight"), "torch paraphrase should resolve flashlight capability");
        check(r.plan().steps().get(0).arguments().get("state").equals("off"), "kill torch should mean off");
    }

    private static void genericAppOpenRoutesOffline() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("open Spotify please");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN && r.plan().steps().get(0).tool().equals("open_app"), "generic open-app request should route locally");
        check(r.plan().steps().get(0).arguments().get("app").equals("Spotify"), "generic app route should preserve visible app name");
        check(calls[0] == 0, "generic app opening should not require a reasoning provider");
    }

    private static void timerRoutesOffline() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("set a timer for 5 minutes");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN && r.plan().steps().get(0).tool().equals("set_timer"), "timer request should route locally");
        check(r.plan().steps().get(0).arguments().get("amount").equals("5") && r.plan().steps().get(0).arguments().get("unit").equals("minutes"), "timer route should preserve amount and unit");
        check(calls[0] == 0, "timer should not require a reasoning provider");
    }

    private static void alarmRoutesOffline() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("set an alarm for 7:30");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN && r.plan().steps().get(0).tool().equals("set_alarm"), "alarm request should route locally");
        check(r.plan().steps().get(0).arguments().get("hour").equals("7") && r.plan().steps().get(0).arguments().get("minute").equals("30"), "alarm route should preserve clock time");
        check(calls[0] == 0, "alarm should not require a reasoning provider");
    }

    private static void volumeRoutesOffline() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("turn the volume down");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN && r.plan().steps().get(0).tool().equals("volume_control"), "volume request should route locally");
        check(r.plan().steps().get(0).arguments().get("action").equals("down"), "volume route should retain direction");
        check(calls[0] == 0, "volume should not require a reasoning provider");
    }

    private static void mediaControlRoutesOffline() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("pause the music");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN && r.plan().steps().get(0).tool().equals("media_control"), "media pause should route locally");
        check(r.plan().steps().get(0).arguments().get("action").equals("pause"), "media route should retain pause action");
        check(calls[0] == 0, "media control should not require a reasoning provider");
    }

    private static void webSearchRoutesOffline() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("search the web for weather in Denver");
        check(r.kind() == BrainResponse.Kind.ACTION_PLAN && r.plan().steps().get(0).tool().equals("web_search"), "web-search request should route locally");
        check(r.plan().steps().get(0).arguments().get("query").equals("weather in Denver"), "web search should preserve the requested query");
        check(calls[0] == 0, "web search should not require a reasoning provider");
    }

    private static void dinnerParaphraseBuildsDiscoveryPlan() {
        Plan plan = new SemanticGoalInterpreter().interpret("find somewhere nearby for food tonight").orElseThrow();
        check(plan.steps().stream().anyMatch(s -> s.tool().equals("discover_places")), "dinner plan needs place discovery");
        check(plan.steps().stream().anyMatch(s -> s.tool().equals("rank_options")), "dinner plan needs ranking");
        check(plan.steps().stream().anyMatch(s -> s.tool().equals("present_options")), "dinner plan needs presentation");
        check(plan.steps().get(0).arguments().get("time").equals("tonight"), "semantic reflex should retain requested dinner timing");
    }

    private static void descriptiveFoodDiscoveryDoesNotBuildDiscoveryPlan() {
        check(new SemanticGoalInterpreter().interpret("Finding somewhere to eat tonight can be stressful.").isEmpty(),
                "descriptive food-discovery language must not become an autonomous discovery plan");
    }

    private static void ambiguousRequestStillFallsThroughToReasoning() {
        int[] calls = {0}; BrainResponse r = core(calls).handle("figure out the best way to reorganize my week");
        check(r.kind() == BrainResponse.Kind.CONVERSATION, "open-ended ambiguous request should be handled by reasoning provider");
        check(calls[0] == 1, "semantic reflex must not overclaim ambiguous intents");
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
