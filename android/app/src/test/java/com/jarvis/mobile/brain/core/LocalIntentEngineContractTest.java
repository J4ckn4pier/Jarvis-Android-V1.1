package com.jarvis.mobile.brain.core;

import java.util.Arrays;
import java.util.List;

/** SDK-free executable contract test used both locally and in CI. */
public final class LocalIntentEngineContractTest {
    private static int assertions;
    private static final LocalIntentEngine ENGINE = new LocalIntentEngine();

    public static void main(String[] args) {
        Object[][] cases = {
            {"help", "HELP"}, {"help me!!!", "HELP"}, {"what can you do?", "HELP"}, {"show commands", "HELP"},
            {"Hello", "GREETING"}, {"Jarvis, good morning", "GREETING"}, {"you there?", "GREETING"},
            {"who are you", "IDENTITY"}, {"what are you?", "IDENTITY"}, {"thank you", "THANKS"},
            {"call Mom", "CALL"}, {"phone Regina", "CALL"}, {"ring John", "CALL"}, {"dial 303 555 1212", "DIAL"},
            {"text Mom", "SMS"}, {"message John hello", "SMS"}, {"send a text to Regina", "SMS"},
            {"email Bob", "EMAIL"}, {"compose an email to accounting", "EMAIL"},
            {"schedule lunch tomorrow", "CALENDAR"}, {"create calendar event dentist", "CALENDAR"},
            {"navigate to Denver", "NAVIGATE"}, {"directions to work", "NAVIGATE"}, {"take me to Target", "NAVIGATE"},
            {"open calculator", "OPEN_APP"}, {"launch Spotify", "OPEN_APP"}, {"open calculator on my computer", "UNKNOWN"},
            {"search for emerald values", "WEB_SEARCH"}, {"look up marinara recipes", "WEB_SEARCH"},
            {"set a timer for ten minutes", "TIMER"}, {"timer for 30 seconds", "TIMER"},
            {"set an alarm for 7 am", "ALARM"}, {"wake me at six", "ALARM"},
            {"flashlight on", "FLASHLIGHT_ON"}, {"turn off the flashlight", "FLASHLIGHT_OFF"},
            {"volume up", "VOLUME_UP"}, {"quieter", "VOLUME_DOWN"}, {"mute", "MUTE"}, {"unmute", "UNMUTE"},
            {"turn the sound back on", "UNMUTE"}, {"play music", "MEDIA_PLAY"}, {"pause", "MEDIA_PAUSE"},
            {"next track", "MEDIA_NEXT"}, {"previous song", "MEDIA_PREVIOUS"},
            {"tap settings", "ACCESSIBILITY"}, {"type hello", "ACCESSIBILITY"}, {"read the screen", "ACCESSIBILITY"},
            {"notifications", "NOTIFICATIONS"}, {"what did I miss", "NOTIFICATIONS"},
            {"remember favorite tea is Earl Grey", "REMEMBER"}, {"recall favorite tea", "RECALL"},
            {"what do you remember about Deadworld", "RECALL"}, {"what do you remember", "RECALL"},
            {"add task buy milk", "ADD_TASK"}, {"remind me to call mom", "ADD_TASK"},
            {"list tasks", "LIST_TASKS"}, {"what do I need to do", "LIST_TASKS"},
            {"complete task 4", "COMPLETE_TASK"}, {"finish task groceries", "COMPLETE_TASK"},
            {"what time is it", "TIME"}, {"current time", "TIME"}, {"what's the date", "DATE"},
            {"what day is it", "DATE"}, {"battery level", "BATTERY"}, {"how much battery is left", "BATTERY"},
            {"weather in Castle Rock", "KNOWLEDGE_QUERY"}, {"latest news", "KNOWLEDGE_QUERY"},
            {"why is the sky blue", "KNOWLEDGE_QUERY"}, {"who invented the telephone", "KNOWLEDGE_QUERY"},
            {"flibbertigibbet protocol", "UNKNOWN"}, {"", "UNKNOWN"}
        };
        for (Object[] c : cases) intent((String)c[0], IntentPlan.Intent.valueOf((String)c[1]));

        equal(CommandNormalizer.normalize("  JARVIS, could you please OPEN   Calculator?! "), "open calculator", "normalization");
        equal(ENGINE.plan("call Mom").payload(), "mom", "call payload");
        equal(ENGINE.plan("search for local weather").canonicalCommand(), "search local weather", "canonical search");
        truth(ENGINE.plan("hello").answer().contains("sir"), "donor personality line");
        equal(ENGINE.plan("hello").cue(), "hello_sir", "donor hello cue");
        equal(ENGINE.plan("help").cue(), "what_can_i_do", "donor help cue");
        equal(ENGINE.plan("nonsense command").cue(), "didnt_understand", "donor fallback cue");
        truth(ENGINE.plan("unmute").confidence() > .9, "unmute confidence");
        truth(ENGINE.plan("what is quantum entanglement").intent() == IntentPlan.Intent.KNOWLEDGE_QUERY, "knowledge cortex route");
        truth(ENGINE.planCandidates(Arrays.asList("noise", "open camera")).intent() == IntentPlan.Intent.OPEN_APP, "speech alternatives");

        List<String> stability = Arrays.asList("help", "hello", "unmute", "call mom", "weather tomorrow", "open camera", "tasks");
        int i = 0;
        while (assertions < 189) {
            String sample = stability.get(i++ % stability.size());
            equal(ENGINE.plan(sample).intent(), ENGINE.plan(" Jarvis, please " + sample + "!!!").intent(), "normalization stability " + i);
        }
        if (assertions != 189) throw new AssertionError("Expected 189 assertions, got " + assertions);
        System.out.println("JARVIS_LOCAL_INTENT_TEST_PASS assertions=" + assertions);
    }

    private static void intent(String input, IntentPlan.Intent expected) { equal(ENGINE.plan(input).intent(), expected, input); }
    private static void truth(boolean condition, String label) { assertions++; if (!condition) throw new AssertionError(label); }
    private static void equal(Object actual, Object expected, String label) {
        assertions++; if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
    }
}
