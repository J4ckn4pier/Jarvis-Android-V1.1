package com.jarvis.brain;

import java.util.Map;

/** Translation must use a provider-neutral gateway and never report synthetic translation-ready success. */
public final class TranslationGatewayContractTest {
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

            @Override public ToolResult translate(Map<String, String> arguments, ExecutionContext context) {
                return ToolResult.success("bonjour");
            }
        };

        ToolRegistry registry = ToolRegistry.standard(gateway);
        ToolRegistry.RegisteredTool translate = registry.resolve("translate")
                .orElseThrow(() -> new AssertionError("translate tool missing"));
        check(translate.spec().executionClass() == ToolExecutionClass.AUTONOMOUS_RESEARCH,
                "translation must route through the provider-neutral reasoning/research boundary");
        ToolResult result = translate.implementation().execute(
                Map.of("request", "translate hello to French"), ExecutionContext.empty());
        check(result.success(), "attached translation gateway should execute successfully");
        check("bonjour".equals(result.message()), "translation result must come from the attached gateway");
        check(!"translation-ready".equals(result.message()), "translation must never report synthetic readiness");
        System.out.println("TranslationGatewayContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
