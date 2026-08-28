package com.jarvis.brain;

import java.util.Map;

public final class DiningResearchCapabilityTest {
    public static void main(String[] args) {
        class FakeGateway implements ExternalResearchGateway {
            int menuCalls;
            int reservationCalls;
            Map<String,String> lastMenu;
            Map<String,String> lastReservation;
            public ToolResult discoverPlaces(Map<String,String> a, ExecutionContext c) { return ToolResult.success("places"); }
            public ToolResult resolveBusiness(Map<String,String> a, ExecutionContext c) { return ToolResult.success("business"); }
            public ToolResult weatherLookup(Map<String,String> a, ExecutionContext c) { return ToolResult.success("weather"); }
            public ToolResult getMenu(Map<String,String> a, ExecutionContext c) { menuCalls++; lastMenu=a; return ToolResult.success("Ribeye|52;Truffle mac|14|source=menu|observed_at=2026-08-28T17:00:00Z"); }
            public ToolResult attemptReservation(Map<String,String> a, ExecutionContext c) { reservationCalls++; lastReservation=a; return ToolResult.success("status=unavailable|available_times=18:00,18:45,20:30"); }
        }
        FakeGateway gateway = new FakeGateway();
        ToolRegistry registry = ToolRegistry.standard(gateway);
        ExecutionContext ctx = new ExecutionContext(new ApprovalGate());

        ToolRegistry.RegisteredTool menu = registry.resolve("get_menu").orElseThrow();
        ToolResult menuResult = menu.implementation().execute(Map.of("business","Example Bistro","items","ribeye,truffle mac"), ctx);
        check(menuResult.isSuccess(), "menu lookup should delegate");
        check(gateway.menuCalls == 1, "menu adapter called once");
        check(menu.spec().executionClass() == ToolExecutionClass.AUTONOMOUS_RESEARCH, "menu is research");

        ToolRegistry.RegisteredTool reservation = registry.resolve("attempt_reservation").orElseThrow();
        check(reservation.spec().consequential(), "reservation submission is consequential");
        check(reservation.spec().executionClass() == ToolExecutionClass.CONSEQUENTIAL, "reservation is consequential class");
        check(reservation.spec().requiredArguments().containsAll(java.util.Set.of("business","party_size","requested_time")), "reservation requires concrete booking args");
        ToolResult reservationResult = reservation.implementation().execute(Map.of("business","Example Bistro","party_size","2","requested_time","19:00"), ctx);
        check(reservationResult.isSuccess(), "booking adapter result should remain visible");
        check(reservationResult.output().contains("available_times"), "real alternatives survive adapter boundary");
        check(!reservationResult.output().toLowerCase().contains("confirmed"), "unavailable response must not fabricate confirmation");

        // Cuisine is data, never an enum: arbitrary values must pass unchanged.
        ToolRegistry.RegisteredTool discover = registry.resolve("discover_places").orElseThrow();
        discover.implementation().execute(Map.of("category","restaurant","cuisine","Ethiopian"), ctx);
        check(discover.spec().requiredArguments().contains("category"), "generic discovery contract remains category based");

        System.out.println("DiningResearchCapabilityTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
