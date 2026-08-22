package io.akka.agno.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Durable session state, keyed by session id. Holds exactly the map
 * {@link io.akka.agno.domain.RunContext} threads through a run — nothing about
 * message history or metrics, which are out of scope for this slice
 * (SPEC-001 §1).
 */
@Component(id = "session-state")
public class SessionStateEntity extends KeyValueEntity<SessionStateEntity.State> {

    public record State(Map<String, Object> values) {
        static State empty() {
            return new State(new HashMap<>());
        }
    }

    @Override
    public State emptyState() {
        return State.empty();
    }

    public ReadOnlyEffect<State> get() {
        return effects().reply(currentState());
    }

    public Effect<State> replace(State newState) {
        return effects().updateState(newState).thenReply(newState);
    }
}
