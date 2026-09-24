# Activity Runtime Compatibility Levels

Status: DESIGN ONLY, 2026-09-24

| Level | Definition | system_server view | Boundary |
|---|---|---|---|
| L0 | Host Activity renders Guest layout/view | Host Activity | Public API; no Guest Activity |
| L1 | Host Activity delegates lifecycle-like callbacks to a Guest object | Host Activity | Guest is not an Activity |
| L2 | Guest ClassLoader creates `GuestActivity extends Activity` without framework attach | Host or no Guest record | Java object only |
| L3 | Guest object is attached using Host carrier token/window/context | Host Stub Activity | token remains Host-owned |
| L4 | Guest launchMode, Intent, result, task, configuration and saved state are translated | Host Stub Activity | high-compatibility virtualization candidate |
| L5 | PMS/ATMS recognize Guest package/component as real installed identity | Guest component | unavailable to ordinary uninstalled-APK Host |

L0/L1 are content/delegation. L2 is object construction. L3 begins framework Activity state. L4 is a translation system. L5 is system identity. Calling `onCreate` or `onResume` manually does not promote L1/L2 to L3/L4.

Current project evidence confirms Controlled Context/resources/minimal Application baselines, not Activity Context. task-17 only designs the next routes and does not change the current runtime.
