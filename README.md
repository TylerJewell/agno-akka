# agno-akka

Given a scripted model turn and a set of tools, this runs the model/tool round-trip loop
to completion, threading one session's state through every tool call and folding a
tool's own failure into its result instead of stopping the run.

A port of [agno-agi/agno](https://github.com/agno-agi/agno) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

agno is a framework for building language-model agents that call tools in a loop. It was
ported to derive a specification format precise enough to regenerate a system on a
different stack — the port is the vehicle, the specification is the deliverable.

The specifications the port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `agno-port/`.

---

## agno-agi/agno → this port

📉 1,524 Python lines → **127 Java lines**<br>
📁 3 files → **9 files**<br>
🎯 6 of 6 shared answers matching → **6 of 6**<br>
⚡ 738,854 → **820** nanoseconds, a two-round tool loop that ends on plain content<br>
⚡ 2,823,291 → **1,069** nanoseconds, a tool that raises<br>
🧪 not measured → **6 tests**<br>
💾 in memory, gone at process exit → **a durable entry, one per session**

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/agno-port/bench/REPORT.md).

---

## What it took to build

⏱️ **0.6 hours** from the first command to the published repository, **0.6** of them active<br>
💬 **362** exchanges with the model<br>
✍️ **136,838** tokens written by the model, **60,382,760** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **6** tests

```bash
python toolkit/tokens.py --port agno    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

- **The loop keeps going as long as the model asks for tools, and stops the moment it
  does not.** A reply carrying tool calls is run and the model is asked again; a reply
  carrying plain text is the answer.
- **Every call the model asks for in one turn runs, in the order asked, whether or not an
  earlier one in the same turn failed.**
- **A tool that raises does not stop the run.** What it raised becomes that call's own
  result, marked as failed, and the loop carries on exactly as it would after a success.
- **Session state is a map handed to any tool that asks for it, not one handed to every
  tool automatically.** A tool that does not ask for it cannot see or change it.
- **A change one tool call makes to session state is there for every call after it in the
  same run**, and is still there once the run finishes.
- **A ceiling on how many tools may run in one turn is a running total for the whole
  run, not per turn.** A call that would push the total over the ceiling does not run,
  and does not appear anywhere in what the caller sees back — not as an error, an
  absence.

---

## Design decisions

**Session state travels through a run context, not a bare parameter.** A tool that wants
it declares that it takes one; nothing is guessed from a parameter's name. This makes
"this tool touches shared state" a fact visible in its type, not something a reader has
to trace through every call site to be sure of.

**The loop is a plain object with no framework underneath it.** Everything the six rules
describe lives in one small class, checkable and testable without starting anything. That
is what lets a single JVM process run the same rule six hundred thousand times a second
and what makes the six tests exercise exactly the rules and nothing else.

**The model and the tools are both scripted, on purpose.** What a real model says, and
what a real tool does, are not part of what this port compares — a request describes a
conversation and a set of tool behaviours up front, and the loop is what gets exercised.
That is what lets the same answers be checked against the original without either side
calling a language model.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/agno-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9052.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

No key for a model provider is needed. Nothing here calls one.

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9052**.

### Try it

```bash
curl -X POST localhost:9052/agents/demo/run -H 'Content-Type: application/json' -d '{
  "sessionId": "demo",
  "script": [
    {"toolCalls": [{"callId": "c1", "toolName": "add"}]},
    {"content": "done"}
  ],
  "tools": [{"name": "add", "returns": "3", "touchesSessionState": false}]
}'

# run it again against the same session id, with a tool that reads and writes
# session state, and watch the count carry over from the call above
curl -X POST localhost:9052/agents/demo/run -H 'Content-Type: application/json' -d '{
  "sessionId": "demo",
  "script": [
    {"toolCalls": [{"callId": "c2", "toolName": "touch"}]},
    {"content": "done"}
  ],
  "tools": [{"name": "touch", "touchesSessionState": true}]
}'
```

### Endpoints

| Method | Path | What it does |
|---|---|---|
| `POST` | `/agents/{sessionId}/run` | runs one scripted turn to completion against that session's durable state |

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| none | — | nothing here is configured by environment variable |

The one setting is the port the service listens on, in
`src/main/resources/application.conf`.

---

## Where it differs from agno-agi/agno

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **What runs when the model is called.** agno calls a language model, which decides what
  content or tool calls come back. Here that is a fixed script supplied with the request.
  Nothing model-backed is built.
- **Where session state lives between runs.** agno keeps it on a session object read from
  and written back to a database the caller configures — SQLite by default, Postgres or
  others in production — with a caller-controlled merge rule for what happens when both
  the caller and the stored copy carry a value for the same key. This port keeps it as one
  durable entry per session id, replaced whole on every run; there is no configurable
  merge rule, because there is only one caller-supplied map per request.
- **Retries around a failed model call.** agno wraps the whole run in an outer retry loop
  with backoff, separate from the tool loop itself. This port has no outer retry — a
  scripted model does not fail the way a real one can, so there was nothing to retry
  against.
- **Everything else agno's run does.** Hooks before and after a run, reasoning steps,
  human-in-the-loop pause and resume, streaming, background memory and learning tasks, and
  telemetry are not rebuilt here and their behaviour is `not checked`. Telemetry in
  particular is measured, not merely unbuilt — `bench/REPORT.md` found it costs about 400
  milliseconds of every real `agent.run()` call by default, none of which is part of what
  this port compares.

---

## Licence

agno-agi/agno is under the Apache License, Version 2.0. This port reimplements the
behaviour without copied code; see `ACKNOWLEDGEMENTS.md`.
