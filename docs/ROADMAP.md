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
EXP-002 API30+ = CONFIRMED
EXP-002 API28/29 = RESEARCH NEEDED
EXP-003 = DESIGN READY / NOT EXECUTED
```

EXP-003 is split into independently gated designs:

```text
EXP-003A Controlled Guest Context
EXP-003B Guest Application instantiation
EXP-003C Minimal Application.onCreate
EXP-003D Compatibility surface (future)
```

No Guest Context, Guest Application, Activity, Hook, Binder, JNI, or native
runtime implementation is authorized by this roadmap yet.
