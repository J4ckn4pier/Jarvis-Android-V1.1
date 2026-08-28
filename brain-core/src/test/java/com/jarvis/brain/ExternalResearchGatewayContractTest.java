package com.jarvis.brain;

import java.util.Map;

public final class ExternalResearchGatewayContractTest {
    private static int checks;

    public static void main(String[] args) {
        injectedGatewayBacksPlaceBusinessAndWeatherTools();
        standardRegistryFailsClosedWhenExternalResearchIsNotAttached();
        revisedPlaceGoalCancelsPriorRequestBeforeRestart();
        System.out.println("ExternalResearchGatewayContractTest: " + checks + " assertions passed");
    }

    private static void injectedGatewayBacksPlaceBusinessAndWeatherTools() {
        int[] placeCalls = {0};
        int[] businessCalls = {0};
        int[] weatherCalls = {0};
        ExternalResearchGateway gateway = new ExternalResearchGateway() {
            public ToolResult discoverPlaces(Map<String, String> arguments, ExecutionContext context) {
                placeCalls[0]++;
                return ToolResult.success("Castle Cafe|distance=0.4mi|open_status=unknown");
            }

            public ToolResult resolveBusiness(Map<String, String> arguments, ExecutionContext context) {
                businessCalls[0]++;
                return ToolResult.success("Castle Cafe|phone=555-0100|source=directory");
            }

            public ToolResult weatherLookup(Map<String, String> arguments, ExecutionContext context) {
                weatherCalls[0]++;
                return ToolResult.success("Monday|72F|source=forecast");
            }
        };

        ToolRegistry registry = ToolRegistry.standard(gateway);
        ToolResult places = registry.resolve("discover_places").orElseThrow().implementation()
                .execute(Map.of("category", "dinner"), new ExecutionContext());
        ToolResult business = registry.resolve("resolve_business").orElseThrow().implementation()
                .execute(Map.of("business", "Castle Cafe"), new ExecutionContext());
        ToolResult weather = registry.resolve("weather_lookup").orElseThrow().implementation()
                .execute(Map.of("when", "Monday"), new ExecutionContext());

        check(places.status() == ToolResult.Status.SUCCESS && places.output().contains("Castle Cafe"),
                "discover_places should delegate to the attached external gateway");
        check(business.status() == ToolResult.Status.SUCCESS && business.output().contains("phone=555-0100"),
                "resolve_business should delegate to the attached external gateway");
        check(weather.status() == ToolResult.Status.SUCCESS && weather.output().contains("72F"),
                "weather_lookup should delegate to the attached external gateway");
        check(placeCalls[0] == 1 && businessCalls[0] == 1 && weatherCalls[0] == 1,
                "each research contract should execute exactly once through the gateway");
    }

    private static void standardRegistryFailsClosedWhenExternalResearchIsNotAttached() {
        ToolRegistry registry = ToolRegistry.standard();
        ToolResult places = registry.resolve("discover_places").orElseThrow().implementation()
                .execute(Map.of("category", "dinner"), new ExecutionContext());
        ToolResult business = registry.resolve("resolve_business").orElseThrow().implementation()
                .execute(Map.of("business", "Castle Cafe"), new ExecutionContext());
        ToolResult weather = registry.resolve("weather_lookup").orElseThrow().implementation()
                .execute(Map.of("when", "Monday"), new ExecutionContext());
        check(places.status() != ToolResult.Status.SUCCESS && !places.output().contains("ready"),
                "place discovery must not pretend to succeed when no real adapter is attached");
        check(business.status() != ToolResult.Status.SUCCESS && !business.output().contains("ready"),
                "business resolution must not pretend to succeed when no real adapter is attached");
        check(weather.status() != ToolResult.Status.SUCCESS && !weather.output().contains("ready"),
                "weather lookup must not pretend to succeed when no real adapter is attached");
    }

    private static void revisedPlaceGoalCancelsPriorRequestBeforeRestart() {
        int[] cancels = {0};
        int[] calls = {0};
        ExternalResearchGateway gateway = new ExternalResearchGateway() {
            public ToolResult discoverPlaces(Map<String, String> arguments, ExecutionContext context) {
                calls[0]++;
                return ToolResult.success(arguments.get("category"));
            }
            public ToolResult resolveBusiness(Map<String, String> arguments, ExecutionContext context) { return ToolResult.failure("unused"); }
            public ToolResult weatherLookup(Map<String, String> arguments, ExecutionContext context) { return ToolResult.failure("unused"); }
        };
        CancellableResearchCoordinator coordinator = new CancellableResearchCoordinator(gateway, () -> cancels[0]++);
        coordinator.beginPlaces(Map.of("category", "Chinese"), new ExecutionContext());
        ToolResult revised = coordinator.restartPlaces(Map.of("category", "Italian"), new ExecutionContext());
        check(cancels[0] == 1, "same-goal research correction must cancel the prior request before restart");
        check(calls[0] == 2, "revised place goal must execute through the same research gateway");
        check("Italian".equals(revised.output()), "restarted request must use revised parameters rather than stale query");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
