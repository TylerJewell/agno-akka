package io.akka.agno.domain;

import java.util.Map;

/** One tool invocation requested by a model turn. */
public record ToolCall(String callId, String toolName, Map<String, Object> args) {
}
