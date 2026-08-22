# Acknowledgements

This project is a port of **[agno-agi/agno](https://github.com/agno-agi/agno)**, commit
`7b8e5308e9c88e30153dfb0619c7b362bed272a2`.

## Licence and copyright

- agno-agi/agno is licensed under the **Apache License, Version 2.0**
  (`LICENSE-agno`, copied verbatim from the source repository's `LICENSE`).
- **No Python was transcribed.** Every Java file under `src` was written fresh against
  behaviour read out of, and run against, the installed `agno` package
  (`libs/agno/agno/agent/_run.py`, `libs/agno/agno/models/base.py`,
  `libs/agno/agno/tools/function.py`). Where a comment or the spec cites a source file
  and line range, that is citation, not copying.
- **Behaviour is derived throughout**: the model/tool round-trip loop, how session state
  is threaded through a run via a run-context object rather than a directly-injected
  parameter, how a raising tool folds into its own result instead of failing the run, and
  how a tool-call budget silently drops calls once exhausted are all ported from the
  decision procedure in the three files above. This is the nature of a port and is not
  something to obscure — see `specs/SPEC-001-agno.md` and `docs/question-log.md` for the
  full evidence trail.
- Apache-2.0 asks that a copy of the licence and any `NOTICE` file travel with
  redistributed copies. `agno-agi/agno` carries no `NOTICE` file at the cloned commit;
  `LICENSE-agno` is carried here regardless.

## Also used

- [Akka](https://akka.io) — the SDK and runtime this port is built on.
