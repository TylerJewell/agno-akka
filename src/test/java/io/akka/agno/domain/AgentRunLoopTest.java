package io.akka.agno.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunLoopTest {

    /** A model that replays a fixed script of turns, one per call. */
    private static Model scripted(ModelTurn... turns) {
        Deque<ModelTurn> script = new ArrayDeque<>(List.of(turns));
        return priorResults -> script.poll();
    }

    private static ToolCall call(String id, String tool) {
        return new ToolCall(id, tool, Map.of());
    }

    @Test
    void multiRoundToolLoopStopsOnPlainContent() {
        AgentTool add = new AgentTool() {
            public String name() { return "add"; }
            public Object call(Map<String, Object> args) { return "sum"; }
        };
        Model model = scripted(
                ModelTurn.toolCalls(List.of(call("c1", "add"))),
                ModelTurn.toolCalls(List.of(call("c2", "add"))),
                ModelTurn.content("done"));

        RunOutput out = new AgentRunLoop(model, List.of(add), null).run("s1", new HashMap<>());

        assertThat(out.content()).isEqualTo("done");
        assertThat(out.tools()).hasSize(2);
        assertThat(out.tools().stream().allMatch(t -> !t.toolCallError())).isTrue();
    }

    @Test
    void toolCallsInOneTurnRunInOrder() {
        List<String> order = new java.util.ArrayList<>();
        AgentTool first = new AgentTool() {
            public String name() { return "first"; }
            public Object call(Map<String, Object> args) { order.add("first"); return "ok"; }
        };
        AgentTool second = new AgentTool() {
            public String name() { return "second"; }
            public Object call(Map<String, Object> args) { order.add("second"); return "ok"; }
        };
        Model model = scripted(
                ModelTurn.toolCalls(List.of(call("c1", "first"), call("c2", "second"))),
                ModelTurn.content("done"));

        new AgentRunLoop(model, List.of(first, second), null).run("s1", new HashMap<>());

        assertThat(order).containsExactly("first", "second");
    }

    @Test
    void toolExceptionBecomesErrorResultNotPropagated() {
        AgentTool blowUp = new AgentTool() {
            public String name() { return "blowUp"; }
            public Object call(Map<String, Object> args) { throw new IllegalStateException("boom from tool"); }
        };
        Model model = scripted(
                ModelTurn.toolCalls(List.of(call("c1", "blowUp"))),
                ModelTurn.content("handled"));

        RunOutput out = new AgentRunLoop(model, List.of(blowUp), null).run("s1", new HashMap<>());

        assertThat(out.content()).isEqualTo("handled");
        assertThat(out.tools()).hasSize(1);
        assertThat(out.tools().get(0).toolCallError()).isTrue();
        assertThat(out.tools().get(0).result()).isEqualTo("boom from tool");
    }

    @Test
    void sessionStateMutationPersistsAcrossRounds() {
        AgentTool.ContextAware touchState = new AgentTool.ContextAware() {
            public String name() { return "touchState"; }
            public Object call(Map<String, Object> args, RunContext runContext) {
                Map<String, Object> state = runContext.sessionState();
                int touched = (int) state.getOrDefault("touched", 0) + 1;
                state.put("touched", touched);
                return "touched=" + touched;
            }
        };
        Model model = scripted(
                ModelTurn.toolCalls(List.of(call("c1", "touchState"))),
                ModelTurn.toolCalls(List.of(call("c2", "touchState"))),
                ModelTurn.content("done"));

        Map<String, Object> sessionState = new HashMap<>();
        RunOutput out = new AgentRunLoop(model, List.of(touchState), null).run("s1", sessionState);

        assertThat(out.sessionState().get("touched")).isEqualTo(2);
        assertThat(out.tools().get(0).result()).isEqualTo("touched=1");
        assertThat(out.tools().get(1).result()).isEqualTo("touched=2");
    }

    @Test
    void toolCallBudgetDropsExcessCallsSilently() {
        AgentTool add = new AgentTool() {
            public String name() { return "add"; }
            public Object call(Map<String, Object> args) { return "sum"; }
        };
        Model model = scripted(
                ModelTurn.toolCalls(List.of(call("c1", "add"), call("c2", "add"))),
                ModelTurn.content("done"));

        RunOutput out = new AgentRunLoop(model, List.of(add), 1).run("s1", new HashMap<>());

        assertThat(out.tools()).hasSize(1);
        assertThat(out.tools().get(0).callId()).isEqualTo("c1");
    }

    @Test
    void toolWithoutRunContextHasNoSessionStateAccess() {
        AgentTool plain = new AgentTool() {
            public String name() { return "plain"; }
            public Object call(Map<String, Object> args) { return "ok"; }
        };
        assertThat(plain instanceof AgentTool.ContextAware).isFalse();

        Model model = scripted(
                ModelTurn.toolCalls(List.of(call("c1", "plain"))),
                ModelTurn.content("done"));

        Map<String, Object> sessionState = new HashMap<>();
        RunOutput out = new AgentRunLoop(model, List.of(plain), null).run("s1", sessionState);

        assertThat(sessionState).isEmpty();
        assertThat(out.tools().get(0).result()).isEqualTo("ok");
    }
}
