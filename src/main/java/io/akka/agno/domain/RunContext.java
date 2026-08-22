package io.akka.agno.domain;

import java.util.Map;

/**
 * Handed to a tool that declares it wants one. {@code sessionState} is the live
 * map for the whole run: a mutation made through one call's run context is visible
 * to every tool call that runs after it in the same run (SPEC-001 R4).
 */
public record RunContext(String sessionId, Map<String, Object> sessionState) {
}
