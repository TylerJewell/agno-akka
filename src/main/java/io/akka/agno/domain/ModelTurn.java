package io.akka.agno.domain;

import java.util.List;

/**
 * One model reply: either plain content (the loop ends, SPEC-001 R1) or one or
 * more tool calls to run before calling the model again.
 */
public record ModelTurn(String content, List<ToolCall> toolCalls) {

    public static ModelTurn content(String content) {
        return new ModelTurn(content, List.of());
    }

    public static ModelTurn toolCalls(List<ToolCall> calls) {
        return new ModelTurn(null, calls);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
