# Critical Assumptions

Status values are restricted to `CONFIRMED`, `UNCONFIRMED`, `PARTIALLY CONFIRMED`, and `BLOCKED`.

| ID | Hypothesis | Evidence currently available | What breaks if false | Test | Priority | Status |
|---|---|---|---|---|---:|---|
| A1 | An uninstalled APK can load ordinary guest classes from a host-controlled loader. | Public `DexClassLoader`/`BaseDexClassLoader` APIs describe APK/dex paths; no project experiment yet. | All later runtime levels. | EXP-001. | 1 | UNCONFIRMED |
| A2 | Guest resources can be created independently of PMS installation. | Archive parsing and public resource types exist; lifecycle/cache behavior untested. | Resource/UI path and any real Application. | EXP-002. | 2 | UNCONFIRMED |
| A3 | A guest Application object can be instantiated with a controlled context. | Java reflection is possible; `Application.attach` environment is not proven. | Application and component lifecycle. | EXP-003. | 3 | UNCONFIRMED |
| A4 | A4.1: a Java Activity object can be manually constructed. | Ordinary Java construction is plausible but Android internals are not equivalent. | Only object-level UI experiment. | Controlled object experiment. | 6 | UNCONFIRMED |
| A4 | A4.2: a real Activity can obtain token/task/window/lifecycle cooperation without guest PMS registration. | ATMS/WMS own system records; no public proof for uninstalled target. | Native Activity semantics. | Dedicated Activity gate. | 4 | BLOCKED pending platform path |
| A5 | A host component can represent a guest logical component while preserving lifecycle/task/result behavior. | Host components are system-visible; mapping is only a proposal. | Strategy A/B and Activity/Service integration. | Host-shell experiment. | 5 | UNCONFIRMED |
| A6 | A guest Context can be safely exposed as a useful Context subset. | Context APIs are public, but many reach system services and package state. | Application and most libraries. | Method-by-method Context contract. | 3 | UNCONFIRMED |
| A7 | A local PackageManager facade covers enough guest calls. | Guest code can obtain PackageManager through multiple framework/library paths. | Compatibility for ordinary apps. | API trace with GuestTestApp. | 7 | UNCONFIRMED |
| A8 | Guest packageName can exist as logical identity without being presented as Android caller identity. | Host UID/package are system-enforced; logical records are controllable. | Attribution, AppOps, package-facing APIs. | Identity observation matrix. | 5 | PARTIALLY CONFIRMED |
| A9 | APK/Dex/Resources can be shared while instance state is isolated. | Immutable bytes are naturally shareable; runtime state is process/class-loader scoped. | Multi-instance design. | Two-instance static/native/resource test. | 8 | UNCONFIRMED |
| A10 | One process can run multiple different guest instances safely enough for MVP. | Class loaders can be separate objects; statics/native/system state remain process-wide. | Concurrent multi-instance claim. | Process/static/native isolation test. | 6 | UNCONFIRMED |
| A11 | A separate sandbox process improves containment without requiring OS modification. | Android processes isolate memory/crashes but host UID/domain remains shared. | Process roadmap and security claims. | Crash and identity experiment. | 5 | PARTIALLY CONFIRMED |
| A12 | Native libraries can load from controlled extracted paths with per-guest collision safety. | Public native loading APIs exist; linker namespaces/JNI behavior vary. | Native tier. | Native experiment after Java path. | 9 | UNCONFIRMED |
| A13 | Guest permission decisions can be mapped to host capabilities without false OS-grant claims. | Host permissions are real; guest state can be local. | Permission-sensitive apps. | Capability matrix and denial tests. | 8 | PARTIALLY CONFIRMED |
| A14 | Guest intent resolution can be independent of PMS for guest-to-guest targets. | Manifest filters can be normalized; exact matching semantics need tests. | Component dispatch. | Resolver corpus. | 6 | UNCONFIRMED |
| A15 | Storage and registry publication can be made crash-consistent in app-private storage. | File APIs exist; OEM interruption behavior untested. | Package/instance integrity. | Failure-injection test. | 4 | UNCONFIRMED |
| A16 | Android 9-15 behavior can share one public-path design. | APIs and policies evolve; current docs identify version sensitivity. | Support matrix and maintenance cost. | Per-release experiments. | 9 | UNCONFIRMED |
| A17 | Android 16 behavior is known enough for compatibility claims. | No verified project experiment in this milestone. | Android 16 support claim. | Device/AOSP research. | 10 | UNCONFIRMED |

## Ordering rule

The first executable assumption is A1. A2 and A3 follow only after A1 is confirmed. A4/A5 are intentionally later because Activity depends on all lower layers plus system cooperation.
