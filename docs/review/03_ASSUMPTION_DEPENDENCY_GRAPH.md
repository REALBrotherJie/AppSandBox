# Assumption Dependency Graph

```text
A15 Storage/registry integrity
          |
A1 Code loading -----------+
          |                |
A2 Resources --------------+--> A3 Application + Context
          |                |              |
A8 Identity ---------------+              v
A13 Permission ------------+-------> A14 Guest resolution
                                           |
                                           v
                              A10/A11 Process model
                                           |
                                           v
                              A4.1 Activity object
                                           |
                              A4.2/A5 System Activity cooperation
                                           |
                                           v
                              System compatibility (A7)
```

## Dependency observations

- A1 does not require Activity, Binder interception, or guest resources.
- A2 requires a valid APK path and package/resource metadata, but not Application.
- A3 depends on A1, A2, A6, and a storage/context contract.
- A14 can be developed as a pure registry function before runtime components.
- A4.2 depends on nearly every lower assumption and must not be the first implementation target.
- A7 must not be a prerequisite for A1/A2; otherwise the bridge becomes an artificial blocker.

## Cycle audit

The proposed cycle is:

```text
Activity Runtime -> System Bridge -> Activity/Task result
```

Break it by defining System Bridge as a lower-level capability provider with no component orchestration dependency. Activity Runtime may request a capability; a capability may return unsupported. Task/result orchestration remains above the bridge.

## Decision

The first gate is a vertical slice only through A1. No later module may be implemented as if its predecessor succeeded until the corresponding gate is recorded.
