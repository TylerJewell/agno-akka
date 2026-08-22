package io.akka.agno.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The model/tool round-trip loop (SPEC-001). Calls the model, and if it asks
 * for tool calls, runs every one of them and calls the model again, until a
 * reply carries no tool calls.
 */
public final class AgentRunLoop {

    private final Model model;
    private final Map<String, AgentTool> tools;
    private final Integer toolCallBudget;

    public AgentRunLoop(Model model, List<AgentTool> tools, Integer toolCallBudget) {
        this.model = model;
        this.tools = new HashMap<>();
        for (AgentTool tool : tools) {
            this.tools.put(tool.name(), tool);
        }
        this.toolCallBudget = toolCallBudget;
    }

    /**
     * Runs one turn to completion. {@code sessionState} is mutated in place by
     * any {@link AgentTool.ContextAware} tool that runs (R4); the same map is
     * returned on {@link RunOutput}.
     */
    public RunOutput run(String sessionId, Map<String, Object> sessionState) {
        RunContext runContext = new RunContext(sessionId, sessionState);
        List<ToolResult> allResults = new ArrayList<>();
        List<ToolResult> priorResults = List.of();
        int callsSoFar = 0;

        while (true) {
            ModelTurn turn = model.call(priorResults);

            if (!turn.hasToolCalls()) {
                return new RunOutput(turn.content(), allResults, sessionState);
            }

            List<ToolResult> roundResults = new ArrayList<>();
            for (ToolCall call : turn.toolCalls()) {
                // R5: the budget is a running total for the whole run, not per
                // turn. A call that would exceed it does not run and does not
                // appear in the caller-visible result list at all.
                if (toolCallBudget != null && callsSoFar >= toolCallBudget) {
                    continue;
                }
                callsSoFar++;
                roundResults.add(runOne(call, runContext));
            }

            allResults.addAll(roundResults);
            priorResults = roundResults;
        }
    }

    private ToolResult runOne(ToolCall call, RunContext runContext) {
        AgentTool tool = tools.get(call.toolName());
        try {
            if (tool == null) {
                throw new IllegalArgumentException("no tool named " + call.toolName());
            }
            Object result;
            if (tool instanceof AgentTool.ContextAware contextAware) {
                result = contextAware.call(call.args(), runContext);
            } else {
                result = tool.call(call.args());
            }
            return ToolResult.success(call, String.valueOf(result));
        } catch (Exception e) {
            // R3: a raising tool folds into its own result; the run continues.
            return ToolResult.failure(call, e.getMessage());
        }
    }
}
