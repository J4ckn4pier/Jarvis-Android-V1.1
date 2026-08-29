package com.jarvis.brain;

import java.util.Map;

/** Ranking/presentation/outcome steps must come from a real provider-neutral adapter, never synthetic *-ready success. */
public final class OrchestrationGatewayContractTest {
    public static void main(String[] args) {
        ExternalResearchGateway gateway = new ExternalResearchGateway() {
            @Override public ToolResult discoverPlaces(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.failure("unused");
            }

            @Override public ToolResult resolveBusiness(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.failure("unused");
            }

            @Override public ToolResult weatherLookup(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.failure("unused");
            }

            @Override public ToolResult rankOptions(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.success("1. Alpha\n2. Beta");
            }

            @Override public ToolResult presentOptions(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.success("Alpha is the strongest match; Beta is the backup.");
            }

            @Override public ToolResult reportOutcome(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.success("Reservation confirmed for 5:00 PM.");
            }
        };

        ToolRegistry registry = ToolRegistry.standard(gateway);
        assertGatewayResult(registry, "rank_options", "1. Alpha\n2. Beta", "ranking-ready");
        assertGatewayResult(registry, "present_options", "Alpha is the strongest match; Beta is the backup.", "presentation-ready");
        assertGatewayResult(registry, "report_outcome", "Reservation confirmed for 5:00 PM.", "report-ready");

        ToolRegistry unavailable = ToolRegistry.standard(ExternalResearchGateway.unavailable());
        check(unavailable.resolve("rank_options").orElseThrow().implementation()
                        .execute(Map.of(), new ExecutionContext()).status() == ToolResult.Status.FAILURE,
                "ranking must fail closed without an adapter");
        check(unavailable.resolve("present_options").orElseThrow().implementation()
                        .execute(Map.of(), new ExecutionContext()).status() == ToolResult.Status.FAILURE,
                "presentation must fail closed without an adapter");
        check(unavailable.resolve("report_outcome").orElseThrow().implementation()
                        .execute(Map.of(), new ExecutionContext()).status() == ToolResult.Status.FAILURE,
                "outcome reporting must fail closed without an adapter");

        System.out.println("OrchestrationGatewayContractTest passed");
    }

    private static void assertGatewayResult(ToolRegistry registry, String toolName, String expected, String synthetic) {
        ToolRegistry.RegisteredTool tool = registry.resolve(toolName)
                .orElseThrow(() -> new AssertionError(toolName + " missing"));
        check(tool.spec().executionClass() == ToolExecutionClass.AUTONOMOUS_RESEARCH,
                toolName + " must stay behind the provider-neutral orchestration boundary");
        ToolResult result = tool.implementation().execute(Map.of(), new ExecutionContext());
        check(result.status() == ToolResult.Status.SUCCESS, toolName + " adapter should execute");
        check(expected.equals(result.output()), toolName + " must return the attached adapter result");
        check(!synthetic.equals(result.output()), toolName + " must never return synthetic readiness");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
