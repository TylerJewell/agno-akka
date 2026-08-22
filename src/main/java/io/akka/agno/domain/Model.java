package io.akka.agno.domain;

import java.util.List;

/**
 * What the run loop calls each round. In the source this is a language model;
 * here it is the same stand-in the port's probes used (`docs/question-log.md`
 * preamble) — the loop's contract does not depend on what a model says, only
 * on the shape of what it returns.
 */
public interface Model {

    ModelTurn call(List<ToolResult> priorResults);
}
