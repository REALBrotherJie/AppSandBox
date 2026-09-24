# Android 16 Activity Launch Chain

状态：RESEARCHED DESIGN，API 36，2026-09-24。

## Stable concepts

ATMS remains the system_server authority for resolution, task placement, results and lifecycle state. `ActivityRecord`, `Task`, activity/window token, `ActivityInfo`, `ClientTransaction`, `LaunchActivityItem`, `ActivityThread`, `LoadedApk`, `ContextImpl.createActivityContext`, `Instrumentation.newActivity` and `Activity.attach` remain the conceptual chain.

## Version-sensitive internals

API36 continues to use `ActivityClientRecord` and transaction execution, but lifecycle, tracing, display/configuration and back-navigation details evolve. Exact fields, constructors, callback ordering, hidden signatures and transaction item classes are not compatibility contracts. `ActivityThread` and `ContextImpl` require per-release adapters. `AppComponentFactory.instantiateActivity` is public from API28, but only controls construction and cannot create an ATMS record or token.

| Area | API31 | API36 | Effect |
|---|---|---|---|
| launch authority | ATMS/ActivityStarter | same authority with newer policy | Stub translation remains necessary |
| client payload | ClientTransaction/LaunchActivityItem | same model, evolving record data | old `H.LAUNCH_ACTIVITY` recipes are insufficient |
| activity context | token/display/config + LoadedApk | same concept, internal details change | Controlled Context is not equivalent |
| instantiation | Instrumentation/AppComponentFactory | same public hooks | public hook cannot create system record |
| back | legacy with newer additions | predictive back/dispatcher is mature | carrier must preserve Host window identity |
| non-SDK | restricted | restrictions continue to evolve | hidden route has high maintenance |

Bottom line: API36 does not expose a public Activity virtualization API. A modern route must translate before system resolution and/or restore after the Host transaction with version-specific client logic.
