package io.akka.agno.domain;

import java.util.Map;

/**
 * A tool whose behaviour is supplied by the caller instead of executing real
 * code — the same substitution the port's probes made for the model, applied
 * to tools too, so the capability's own HTTP surface can exercise the loop
 * without any real integration behind it. {@code failWith} takes priority
 * over {@code returns}: a scripted tool that names a failure always raises.
 */
public record ScriptedTool(String name, String returns, String failWith, boolean touchesSessionState)
        implements AgentTool.ContextAware {

    @Override
    public Object call(Map<String, Object> args, RunContext runContext) {
        if (failWith != null) {
            throw new RuntimeException(failWith);
        }
        if (touchesSessionState) {
            Map<String, Object> state = runContext.sessionState();
            int touched = (int) state.getOrDefault("touchCount", 0) + 1;
            state.put("touchCount", touched);
        }
        return returns;
    }
}
