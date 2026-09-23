# EXP-003B LoadedApk Route Comparison

## Route A: Accept Host LoadedApk

Status: `LIMITED`

Advantages: public `Instrumentation.newApplication` shape, minimal framework
coupling, and C0 getters can remain Guest-routed through Controlled Context.

Limitations: internal ContextImpl operations, application context lookup,
system services, receiver/service dispatch, callbacks, framework-derived
class loader/resources, and same-package cache behavior can remain Host-backed.

Compatibility ceiling: A1/A2-like controlled getter and simple Guest code
compatibility only; not broad arbitrary Application compatibility.

## Route B: Guest-specific framework LoadedApk

Status: `PROPOSED`

Possible mechanism: construct or obtain a framework `LoadedApk` from Guest
ApplicationInfo, create a framework `ContextImpl`, then use the normal
Instrumentation/Application path.

Classification: hidden/framework-internal. Candidates include
`ActivityThread.getPackageInfoNoCheck`, `LoadedApk` construction,
`ContextImpl.createAppContext`, package/resource cache manipulation, and
framework application registration.

Risks include non-SDK restrictions, target-SDK behavior, Android/OEM coupling,
resource/class-loader cache pollution, package collision, Play policy, and
unclear cleanup. Same-package multi-instance remains critical.

## Route C: Non-framework Application bootstrap

Status: `LIMITED`

Constructing a Guest Application as an ordinary Java object with a custom
bootstrap can support a reduced compatibility runtime, but is not normal
Android Application attach semantics. Framework callbacks, application
context, component dispatch, service managers, lifecycle ordering, and SDKs
expecting a real Context may fail.

## Route D: Interception/compatibility layer

Status: `PROPOSED`

Keep Host framework state but intercept or wrap Context, PackageManager,
system-service, Binder, and component paths. This can reduce individual leaks
but creates broad version/OEM maintenance and identity consistency risks.
No interception is implemented.

## Route E: Separate Host sandbox process

Status: `PROPOSED`

A per-instance process improves crash, static, native, and memory fault
containment. It does not itself create Guest system identity or Guest
LoadedApk: the process still belongs to the Host package without deeper
framework integration.

## Compatibility ceiling

| Route | Highest defensible current level |
|---|---|
| Pure public API | C0/A1 controlled getters and selected Guest code |
| Hidden framework integration | Potential A2-like framework Context, unconfirmed and version-sensitive |
| Interception layer | Selected compatibility surfaces; broad behavior unstable |
| Separate process | Better process/native/crash isolation; no automatic Guest identity |

## Recommended route

`PROPOSED`: retain EXP-003A C0 as a bounded public baseline, design B0 to
measure actual Host LoadedApk impact, and postpone hidden LoadedApk work until
cache and identity behavior are experimentally characterized.
