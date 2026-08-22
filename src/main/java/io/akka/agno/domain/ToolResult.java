package io.akka.agno.domain;

/**
 * The outcome of running one {@link ToolCall}. On failure {@code result} is the
 * exception's message string, never a stack trace or exception type name
 * (SPEC-001 R3) — the same shape a caller sees whether the tool succeeded or
 * raised.
 */
public record ToolResult(String callId, String toolName, boolean toolCallError, String result) {

    public static ToolResult success(ToolCall call, String result) {
        return new ToolResult(call.callId(), call.toolName(), false, result);
    }

    public static ToolResult failure(ToolCall call, String errorMessage) {
        return new ToolResult(call.callId(), call.toolName(), true, errorMessage);
    }
}
