# EXP-003: Guest Application and Controlled Context

Status: Design ready, not executed (2026-09-23).

Question: can a declared Application be instantiated and run `onCreate` with a deliberately limited guest Context?

Hypothesis: a simple Application using only guest paths/resources may work; real applications will expose unsupported system-service assumptions.

Setup: first create a minimal Controlled Context design and use the existing
Guest ClassLoader and EXP-002 Option B resources. Execute gates in order:
EXP-003A Context only, EXP-003B Application instantiation without `onCreate`,
and EXP-003C minimal `onCreate`.

Observe: class loader, resource owner, package name, files directory, lifecycle order, exceptions, thread/context-loader leakage, and teardown.

No Activity, Binder hook, hidden API, native library, external service,
production Guest Context class, or Guest Application implementation in the
design round.

Exit: constructor and `onCreate` behavior are deterministic for the declared
subset. Any Host path/package/resource/loader leak is a failure. Results
determine separate ADR gates for Context, instantiation, and `onCreate`.

See:

- `EXP-003-ANDROID-APPLICATION-MODEL.md`
- `EXP-003-APPLICATION-STRATEGY.md`
- `EXP-003-CONTEXT-CONTRACT.md`
- `EXP-003A-CONTEXT-PLAN.md`
- `EXP-003B-APPLICATION-INSTANTIATION-PLAN.md`
- `EXP-003C-APPLICATION-ONCREATE-PLAN.md`
