package com.jarvis.brain;

import java.util.Map;
import java.util.Set;

/** Shared conversational-call tool contract must be concrete, consequential, and fail closed without transport. */
public final class ConversationalCallToolContractTest {
    public static void main(String[] args) throws Exception {
        ToolRegistry.RegisteredTool call = ToolRegistry.standard().resolve("place_conversational_call").orElseThrow();

        check(call.spec().consequential(),
                "conversational calling must remain approval-gated");
        check(call.spec().executionClass() == ToolExecutionClass.CONSEQUENTIAL,
                "conversational calling must remain a consequential execution class");
        check(call.spec().requiredArguments().containsAll(Set.of(
                        "business", "destination", "represented_user", "preferred_time")),
                "conversational calling must require a resolved destination plus explicit represented user and preferred time");

        ToolResult unattached = call.implementation().execute(Map.of(
                "business", "Lost Coffee",
                "destination", "+13035550199",
                "represented_user", "Charles",
                "preferred_time", "5 PM"), new ExecutionContext());
        check(unattached.status() == ToolResult.Status.FAILURE,
                "shared provider-neutral fallback must fail when no duplex transport is attached");
        check(unattached.output().toLowerCase(java.util.Locale.ROOT).contains("telephony adapter not attached"),
                "unattached fallback must expose the real transport blocker rather than synthetic success");

        System.out.println("ConversationalCallToolContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
