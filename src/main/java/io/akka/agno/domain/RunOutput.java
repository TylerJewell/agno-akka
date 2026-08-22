package io.akka.agno.domain;

import java.util.List;
import java.util.Map;

/** What a run leaves behind: the final content and every tool call that actually ran. */
public record RunOutput(String content, List<ToolResult> tools, Map<String, Object> sessionState) {
}
