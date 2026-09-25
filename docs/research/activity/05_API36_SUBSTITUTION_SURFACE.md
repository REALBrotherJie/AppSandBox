# API36 Activity Substitution Surface

状态：PREFLIGHT RESEARCH，Android 16 / API36，2026-09-25。来源锚点为 `SOURCES.md` 的 A09-A17、A29-A33。

## Stable chain, unstable shape

API36 保留 `ClientTransaction -> launch item -> ActivityThread -> LoadedApk/ContextImpl -> Instrumentation/AppComponentFactory -> Activity.attach -> lifecycle` 的概念链。不能复用 API31 字段名、构造器、回调顺序或反射查找；API36 必须独立发现 adapter surface。

## Surface inventory

| Symbol/state | Role and source relationship | Classification | First probe |
|---|---|---|---|
| `ActivityThread` launch handling | client coordinator for transaction, context creation, instantiation and attach | INTERNAL, VERSION-SENSITIVE, OEM-SENSITIVE | observe type/order only |
| `ActivityClientRecord` | carries Host token, Intent, ActivityInfo, configuration/state and Activity reference | HIDDEN/NON-SDK, VERSION-SENSITIVE | runtime shape inventory only |
| `ClientTransaction` | transaction envelope associated with the client/activity token | HIDDEN/NON-SDK, VERSION-SENSITIVE | observe only |
| `LaunchActivityItem` | launch payload/callback concept; implementation and parcel shape are release-sensitive | HIDDEN/NON-SDK, VERSION-SENSITIVE | observe only |
| transaction executor path | orders callback and lifecycle items | INTERNAL, VERSION-SENSITIVE | order evidence only |
| `Instrumentation.newActivity(ClassLoader,String,Intent)` | public object factory supplied from internal Host launch state | PUBLIC | call-site observation only |
| `AppComponentFactory.instantiateActivity` | public factory API that may participate in instantiation | PUBLIC, framework-controlled invocation | NOT REQUIRED FOR FIRST PROBE |
| `Activity.attach` | binds Context/thread/instrumentation/token/Application/Intent/ActivityInfo/configuration/Window state | INTERNAL, VERSION-SENSITIVE, OEM-SENSITIVE | prohibited |
| `LoadedApk` | Host package runtime selected from system-approved application metadata | INTERNAL | identity observation only |
| `ContextImpl.createActivityContext` | creates Activity-specific context from package/token/display/config inputs | INTERNAL, VERSION-SENSITIVE | order evidence only |
| Host `ActivityInfo` and Intent | continue to describe the installed Host Stub transaction | system-origin/public data objects | observe; do not mutate |
| token/display/config/window/back state | Host carrier state with newer display, window and predictive-back integration | INTERNAL relationship, OEM-SENSITIVE | presence/order only |

## API36 constraints

Target SDK 36 does not make non-SDK interfaces stable or generally accessible. Reflection or linkage may be denied; restricted lists and OEM builds can differ. Access denial is valid P0 evidence and must produce `FAILED_CLOSED` while Host Stub continues normally.

Pre-attach rejection covers unsupported runtime shape, access denial, missing mapping, invalid artifact, missing class and incompatible type. Transaction/client-record mutation risks executor invariants. Beginning attach couples Activity Context, Application, token, ActivityInfo, configuration, Window and back integration and is the irreversible boundary for this plan.

## API36 P0 gate

The first probe may inventory observable transaction/client-record type names and ordering only. It must not assume API31 member names, suppress hidden-API enforcement, mutate payloads, replace Instrumentation, instantiate Guest Activity, invoke attach or touch lifecycle.
