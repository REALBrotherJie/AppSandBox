# Roadmap

> **Status: Superseded.** See [design/24_IMPLEMENTATION_ROADMAP.md](design/24_IMPLEMENTATION_ROADMAP.md) for milestone goals, dependencies, experiments, exit criteria, and risks.

0. Clean-room rules, project, experiments, and test harness
1. APK import and platform metadata parsing
2. Guest package data model and package registry
3. Guest DEX/class loading experiments
4. Guest resource loading experiments
5. Guest Application creation
6. Minimal guest Activity runtime
7. Activity lifecycle
8. Service runtime
9. BroadcastReceiver runtime
10. ContentProvider runtime
11. System service compatibility
12. Guest storage isolation
13. Multiple instances
14. Android version compatibility
15. Stability, security, and performance

Phase 2 must first define registry identity, version/update semantics, corruption recovery, and concurrency behavior from Android/AOSP requirements. It must not implement runtime or hook mechanisms.

## Experiment Status

```text
EXP-001 = CONFIRMED
EXP-002 Legacy = REJECTED
EXP-002 Option A = REJECTED
EXP-002 Option B API31 = CONFIRMED (TESTED ANDROID 12/API31 PATH)
EXP-002 API28/29 = RESEARCH NEEDED
EXP-003A = CONFIRMED C0 DEBUG BASELINE
EXP-003B = DESIGNED / BLOCKED BY LOADEDAPK PRECHECK
EXP-003C = NOT READY
V-1 API34+ read-only DexClassLoader check = BLOCKED_NO_DEVICE
```

EXP-003 is split into independently gated designs:

```text
EXP-003A Controlled Guest Context = CONFIRMED C0
EXP-003B Guest Application instantiation = BLOCKED
EXP-003C Minimal Application.onCreate = NOT READY
EXP-003D Compatibility surface (future)
```


No Guest Context, Guest Application, Activity, Hook, Binder, JNI, or native
runtime implementation is authorized by this roadmap yet.
