package io.akka.agno.bench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.akka.agno.domain.AgentRunLoop;
import io.akka.agno.domain.AgentTool;
import io.akka.agno.domain.Model;
import io.akka.agno.domain.ModelTurn;
import io.akka.agno.domain.RunOutput;
import io.akka.agno.domain.ScriptedTool;
import io.akka.agno.domain.ToolCall;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Runs `bench/workloads.json` through {@link AgentRunLoop} directly — in-process,
 * the same way `AgentRunLoopTest` calls it — and prints answers or per-workload
 * timings as JSON, mirroring `bench/source_run.py`'s two modes.
 *
 * <p>Usage: {@code BenchRunner workloads.json answers|timings [out.json]}
 */
public final class BenchRunner {

    public static void main(String[] args) throws Exception {
        Path workloadsPath = Path.of(args[0]);
        String mode = args[1];
        ObjectMapper mapper = new ObjectMapper();
        JsonNode workloads = mapper.readTree(Files.readString(workloadsPath));

        if (mode.equals("timings")) {
            // Warm up the JIT across every workload before timing any of them,
            // so the first workload timed does not silently pay for the others'
            // warmup.
            for (int i = 0; i < 2000; i++) {
                for (JsonNode w : workloads) {
                    runOnce(mapper, w);
                }
            }
        }

        ObjectNode out = mapper.createObjectNode();
        for (JsonNode w : workloads) {
            String name = w.get("name").asText();
            if (mode.equals("answers")) {
                out.set(name, runOnce(mapper, w));
            } else if (mode.equals("timings")) {
                int reps = 2000;
                long start = System.nanoTime();
                for (int i = 0; i < reps; i++) {
                    runOnce(mapper, w);
                }
                long elapsed = System.nanoTime() - start;
                out.put(name, (double) elapsed / reps);
            } else {
                throw new IllegalArgumentException("unknown mode " + mode);
            }
        }

        String text = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        if (args.length > 2) {
            Files.writeString(Path.of(args[2]), text);
        } else {
            System.out.println(text);
        }
    }

    private static JsonNode runOnce(ObjectMapper mapper, JsonNode workload) {
        List<AgentTool> tools = new ArrayList<>();
        for (JsonNode t : workload.get("tools")) {
            tools.add(new ScriptedTool(
                    t.get("name").asText(),
                    t.hasNonNull("returns") ? t.get("returns").asText() : null,
                    t.hasNonNull("failWith") ? t.get("failWith").asText() : null,
                    t.get("touchesSessionState").asBoolean()));
        }

        Deque<ModelTurn> script = new ArrayDeque<>();
        for (JsonNode turn : workload.get("script")) {
            ArrayNode toolCallsNode = (ArrayNode) turn.get("toolCalls");
            if (toolCallsNode == null || toolCallsNode.isEmpty()) {
                script.add(ModelTurn.content(turn.hasNonNull("content") ? turn.get("content").asText() : null));
            } else {
                List<ToolCall> calls = new ArrayList<>();
                for (JsonNode c : toolCallsNode) {
                    calls.add(new ToolCall(c.get("callId").asText(), c.get("toolName").asText(), Map.of()));
                }
                script.add(ModelTurn.toolCalls(calls));
            }
        }
        Model model = priorResults -> script.poll();

        Integer budget = workload.hasNonNull("toolCallBudget") ? workload.get("toolCallBudget").asInt() : null;
        RunOutput result = new AgentRunLoop(model, tools, budget).run("bench", new java.util.HashMap<>());

        ObjectNode node = mapper.createObjectNode();
        node.put("content", result.content());
        ArrayNode toolsNode = mapper.createArrayNode();
        for (var t : result.tools()) {
            ObjectNode tn = mapper.createObjectNode();
            tn.put("callId", t.callId());
            tn.put("toolName", t.toolName());
            tn.put("toolCallError", t.toolCallError());
            tn.put("result", t.result());
            toolsNode.add(tn);
        }
        node.set("tools", toolsNode);
        ObjectNode stateNode = mapper.createObjectNode();
        result.sessionState().forEach((k, v) -> stateNode.set(k, mapper.valueToTree(v)));
        node.set("sessionState", stateNode);
        return node;
    }
}
