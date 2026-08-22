package io.akka.agno.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.akka.agno.application.SessionStateEntity;
import io.akka.agno.domain.AgentRunLoop;
import io.akka.agno.domain.AgentTool;
import io.akka.agno.domain.Model;
import io.akka.agno.domain.ModelTurn;
import io.akka.agno.domain.RunOutput;
import io.akka.agno.domain.ScriptedTool;
import io.akka.agno.domain.ToolCall;
import io.akka.agno.domain.ToolResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The run loop's own surface: given a session id, a scripted model turn-by-turn
 * conversation and a set of scripted tools, run one agent turn to completion and
 * read back what it produced (SPEC-001).
 *
 * <p>What a real model says, and what a real tool does, are both out of scope
 * (SPEC-001 §1) — a request carries a script for both, the same substitution
 * the port's probes made against the source and for the same reason.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/agents")
public class AgentRunEndpoint {

    private final ComponentClient componentClient;

    public AgentRunEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record ScriptedToolCall(String callId, String toolName) {
    }

    public record ScriptedTurn(String content, List<ScriptedToolCall> toolCalls) {
    }

    public record ScriptedToolSpec(String name, String returns, String failWith, boolean touchesSessionState) {
    }

    public record RunRequest(
            String sessionId,
            List<ScriptedTurn> script,
            List<ScriptedToolSpec> tools,
            Integer toolCallBudget) {
    }

    public record ToolResultResponse(String callId, String toolName, boolean toolCallError, String result) {
    }

    public record RunResponse(String content, List<ToolResultResponse> tools, Map<String, Object> sessionState) {
    }

    @Post("/{sessionId}/run")
    public RunResponse run(String sessionId, RunRequest request) {
        Map<String, Object> sessionState =
                componentClient.forKeyValueEntity(sessionId).method(SessionStateEntity::get).invoke().values();

        Deque<ModelTurn> turns = new ArrayDeque<>();
        for (ScriptedTurn turn : request.script()) {
            if (turn.toolCalls() == null || turn.toolCalls().isEmpty()) {
                turns.add(ModelTurn.content(turn.content()));
            } else {
                List<ToolCall> calls = turn.toolCalls().stream()
                        .map(c -> new ToolCall(c.callId(), c.toolName(), Map.of()))
                        .toList();
                turns.add(ModelTurn.toolCalls(calls));
            }
        }
        Model model = priorResults -> turns.poll();

        List<AgentTool> tools = request.tools().stream()
                .<AgentTool>map(t -> new ScriptedTool(t.name(), t.returns(), t.failWith(), t.touchesSessionState()))
                .toList();

        RunOutput output = new AgentRunLoop(model, tools, request.toolCallBudget())
                .run(sessionId, sessionState);

        componentClient.forKeyValueEntity(sessionId)
                .method(SessionStateEntity::replace)
                .invoke(new SessionStateEntity.State(output.sessionState()));

        List<ToolResultResponse> toolResults = output.tools().stream()
                .map(t -> new ToolResultResponse(t.callId(), t.toolName(), t.toolCallError(), t.result()))
                .toList();
        return new RunResponse(output.content(), toolResults, output.sessionState());
    }
}
