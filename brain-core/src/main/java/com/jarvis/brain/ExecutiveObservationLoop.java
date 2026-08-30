package com.jarvis.brain;

import java.util.List;

/**
 * Bounded prefrontal agent loop: reason -> validate -> execute only safe tools -> observe -> reason again.
 * Consequential work stops before execution and is returned as an explicit approval boundary.
 */
public final class ExecutiveObservationLoop {
    private final ReasoningRouter reasoning;
    private final ToolRegistry registry;
    private final ApprovalGate approvals;
    private final PlanValidator validator;
    private final int maxIterations;

    public ExecutiveObservationLoop(ReasoningRouter reasoning, ToolRegistry registry, ApprovalGate approvals, int maxIterations) {
        if (reasoning == null) throw new IllegalArgumentException("reasoning router required");
        if (registry == null) throw new IllegalArgumentException("tool registry required");
        this.reasoning = reasoning;
        this.registry = registry;
        this.approvals = approvals == null ? new ApprovalGate() : approvals;
        this.validator = new PlanValidator(registry);
        this.maxIterations = Math.max(1, maxIterations);
    }

    public ExecutiveOutcome run(String goal, String initialContext) {
        return runInternal(goal, initialContext, null);
    }

    /** Continue from a reasoning result the caller already obtained, avoiding a duplicate first model call. */
    public ExecutiveOutcome runFrom(String goal, String initialContext, ReasoningResult initialResult) {
        if (initialResult == null) return run(goal, initialContext);
        return runInternal(goal, initialContext, initialResult);
    }

    private ExecutiveOutcome runInternal(String goal, String initialContext, ReasoningResult initialResult) {
        String context = initialContext == null ? "" : initialContext;
        ExecutionContext executionContext = new ExecutionContext();
        String lastText = "";
        SessionStateDelta latestStateDelta = SessionStateDelta.empty();

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            ReasoningResult result;
            if (iteration == 1 && initialResult != null) {
                result = initialResult;
            } else {
                try {
                    result = reasoning.reason(new ReasoningRequest(goal, context, registry.specs()));
                } catch (RuntimeException providerFailure) {
                    return outcome(ExecutiveOutcome.Status.FAILED,
                            "I couldn't complete the reasoning step safely.", null, iteration, context, latestStateDelta);
                }
                if (result == null) {
                    return outcome(ExecutiveOutcome.Status.FAILED,
                            "I couldn't complete the reasoning step safely.", null, iteration, context, latestStateDelta);
                }
            }
            lastText = result.text() == null ? "" : result.text();
            if (result.stateDelta() != null && !result.stateDelta().isEmpty()) latestStateDelta = result.stateDelta();

            if (result.plan() == null) {
                return outcome(ExecutiveOutcome.Status.ANSWERED, lastText, null, iteration, context, latestStateDelta);
            }

            PlanValidation validation = validator.validate(result.plan());
            if (!validation.valid()) {
                return outcome(ExecutiveOutcome.Status.CLARIFICATION_REQUIRED,
                        clarification(validation.errors()), null, iteration, context, latestStateDelta);
            }
            Plan plan = validation.effectivePlan();

            boolean producedObservation = false;
            for (int stepIndex = 0; stepIndex < plan.steps().size(); stepIndex++) {
                PlanStep step = plan.steps().get(stepIndex);
                ToolRegistry.RegisteredTool tool = registry.resolve(step.tool()).orElse(null);
                if (tool == null) {
                    return outcome(ExecutiveOutcome.Status.CLARIFICATION_REQUIRED,
                            "I need to revise that plan before I can continue safely.", null, iteration, context, latestStateDelta);
                }

                boolean consequential = step.consequential() || tool.spec().consequential();
                if (consequential) {
                    Plan pending = remainingPlan(plan, stepIndex);
                    return outcome(ExecutiveOutcome.Status.APPROVAL_REQUIRED,
                            lastText.isBlank() ? "I need your approval before I do that." : lastText,
                            pending, iteration, context, latestStateDelta);
                }

                ToolResult toolResult;
                try {
                    toolResult = normalizeToolResult(tool.implementation().execute(step.arguments(), executionContext));
                    if (toolResult.status() == ToolResult.Status.RETRYABLE_FAILURE) {
                        toolResult = normalizeToolResult(tool.implementation().execute(step.arguments(), executionContext));
                    }
                } catch (RuntimeException failure) {
                    toolResult = ToolResult.failure(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
                }

                String status = toolResult.status() == ToolResult.Status.SUCCESS ? "SUCCESS" : "FAILED";
                context = appendObservation(context, tool.name(), status, toolResult.output());
                producedObservation = true;
                executionContext.put("last_tool", tool.name());
                executionContext.put("last_output", toolResult.output());
                if (toolResult.status() != ToolResult.Status.SUCCESS) break;
            }

            if (!producedObservation) {
                return outcome(ExecutiveOutcome.Status.FAILED,
                        "I couldn't make progress on that plan safely.", null, iteration, context, latestStateDelta);
            }
        }

        return outcome(ExecutiveOutcome.Status.ITERATION_LIMIT,
                iterationLimitText(lastText, context), null, maxIterations, context, latestStateDelta);
    }

    private static ToolResult normalizeToolResult(ToolResult result) {
        return result == null ? ToolResult.failure("tool returned no result") : result;
    }

    private static ExecutiveOutcome outcome(ExecutiveOutcome.Status status, String text, Plan pendingPlan,
                                            int iterations, String context, SessionStateDelta stateDelta) {
        return new ExecutiveOutcome(status, text, pendingPlan, iterations, context, stateDelta);
    }

    private static Plan remainingPlan(Plan plan, int firstUnexecutedIndex) {
        List<PlanStep> remaining = List.copyOf(plan.steps().subList(firstUnexecutedIndex, plan.steps().size()));
        return new Plan(plan.goal(), remaining);
    }

    private static String appendObservation(String context, String tool, String status, String output) {
        String observation = "[TOOL_OBSERVATION status=" + status + " tool=" + safe(tool) + "] " + safe(output);
        if (context == null || context.isBlank()) return observation;
        return context + "\n" + observation;
    }

    private static String iterationLimitText(String lastText, String context) {
        String evidence = lastObservationOutput(context);
        StringBuilder response = new StringBuilder("I couldn't fully complete this within my safe reasoning limit.");
        if (!evidence.isBlank()) {
            response.append(" Here's the best result I found so far: ").append(evidence).append('.');
        } else if (lastText != null && !lastText.isBlank()) {
            response.append(" The last useful reasoning result was: ").append(safe(lastText)).append('.');
        }
        response.append(" What would you like me to check next or clarify?");
        return response.toString();
    }

    private static String lastObservationOutput(String context) {
        if (context == null || context.isBlank()) return "";
        int marker = context.lastIndexOf("[TOOL_OBSERVATION ");
        if (marker < 0) return "";
        int close = context.indexOf("] ", marker);
        if (close < 0) return "";
        int start = close + 2;
        int end = context.indexOf('\n', start);
        if (end < 0) end = context.length();
        return safe(context.substring(start, end));
    }

    private static String clarification(List<String> errors) {
        if (errors == null || errors.isEmpty()) return "I need one more detail before I can continue safely.";
        return "I need clarification before I continue: " + String.join("; ", errors);
    }

    private static String safe(String value) { return value == null ? "" : value.replace('\n', ' ').trim(); }
}