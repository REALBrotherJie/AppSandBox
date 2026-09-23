# Activity Architecture Review

## Required pieces

An Activity is not only a Java object. The client side includes `Activity`, `ActivityThread`, `Instrumentation`, `ActivityClientRecord`, lifecycle callbacks, `Window`, and context. The system side includes ATMS/AMS, `ActivityRecord`, `Task`, window management, token/session state, process association, configuration, permissions, and result/task policy. Transactions such as `ClientTransaction` and `LaunchActivityItem` connect system scheduling to client execution.

## Classification

| Capability | Client-side controllable | System-side required | Hybrid |
|---|---|---|---|
| Java class construction | yes | no | no |
| Guest fields/lifecycle callback invocation | partly | no | yes |
| Guest resources/theme | partly | no | yes |
| Activity token | no | yes | no |
| Task/back stack | no | yes | no |
| Window/session | partly | WMS cooperation | yes |
| Configuration | consume locally | system changes originate in system | hybrid |
| Result delivery | callback object locally | task/record routing | hybrid |
| Process death/restart | no | AMS/Zygote policy | no |
| Saved state | consume data | system owns lifecycle timing | hybrid |

## Review of current design

The previous Activity document correctly listed three strategies but understated that Strategy A/B are not equivalent to native Activity semantics. The missing distinction is “host surface carrying guest UI” versus “guest Activity recognized by ATMS.”

## Strategies

### A: host Activity with guest View/UI

System sees a real host Activity. Guest logic and Views are embedded.

Compatibility: lowest implementation complexity, limited Activity semantics. Task and window belong to host. Preferred as an early UI experiment only.

### B: registered host shell mapping to guest logical Activity

ATMS sees a declared host shell. A runtime maps the requested logical component to guest code.

Compatibility: potentially better lifecycle entry points, but token/task/result/configuration still belong to shell records. Mapping errors can cause identity and reentrancy problems. Requires a dedicated experiment; not an implementation decision.

### C: independent orchestration/process

Use a separate guest process and explicit host-owned orchestration, possibly with a custom surface protocol.

Compatibility: crash containment improves, but system Activity/task semantics remain host-owned unless the OS participates. IPC and rendering complexity increase.

## Preferred hypothesis

Use Strategy A to validate Level 6 UI before any Activity claim. Treat Strategy B as the first Activity-specific hypothesis. Strategy C is a process/isolation option, not proof of native Activity semantics.

## Gate

No Activity Runtime code until a test demonstrates launch, back, result, configuration, saved state, process death, and teardown for the chosen scope on defined Android versions.
