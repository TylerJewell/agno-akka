package io.akka.agno.domain;

import java.util.Map;

/**
 * A tool a model turn can call. Plain tools never see session state at all
 * (SPEC-001 R6); a tool that needs it implements {@link ContextAware} instead,
 * which is a distinct type rather than an ignorable parameter, so "did not ask
 * for it" and "asked for it and ignored it" cannot be confused.
 */
public interface AgentTool {

    String name();

    /**
     * Runs the tool. May throw — a raise here is caught by the run loop and
     * folded into a failed {@link ToolResult} rather than propagated
     * (SPEC-001 R3).
     */
    Object call(Map<String, Object> args);

    /** A tool that reads or mutates session state through a {@link RunContext}. */
    interface ContextAware extends AgentTool {

        @Override
        default Object call(Map<String, Object> args) {
            throw new UnsupportedOperationException("ContextAware tool called without a RunContext");
        }

        Object call(Map<String, Object> args, RunContext runContext);
    }
}
