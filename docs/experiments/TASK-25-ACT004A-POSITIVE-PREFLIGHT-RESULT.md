# TASK-25 ACT-004A Positive Versioned Client Adapter Preflight

日期：2026-09-25

## Evidence reconciliation

The saved final task24 evidence records API31 Host Stub `taskId=57` and API36 Host Stub `taskId=14`. The previous result document listed 54/12 from an earlier run. Task IDs are variable Host observations, not Guest identity or a fixed contract. The result document was minimally corrected to cite the final retained evidence; raw task24 evidence was not changed.

`ACT-004A-NEGATIVE = CONFIRMED` means the public-only ceiling is proven. It does not authorize positive implementation. The vocabulary is now explicit: positive preflight/design is ready; positive implementation remains unauthorized.

## Current gate

```text
ACT-004A-NEGATIVE = CONFIRMED
Positive preflight/design = READY
Positive substitution implemented = NO
Guest Activity attached = NO
Guest lifecycle executed = NO
L3 = NOT CONFIRMED
Host token/window ownership = HOST
```

## API31 surface

`04_API31_SUBSTITUTION_SURFACE.md` inventories ActivityThread launch methods, ActivityClientRecord, ClientTransaction/LaunchActivityItem, Instrumentation/AppComponentFactory, LoadedApk, ContextImpl, ActivityInfo/Intent, token/config/display/Window and the attach boundary. P0 may observe runtime type/order only.

## API36 surface

`05_API36_SUBSTITUTION_SURFACE.md` preserves the stable conceptual chain but requires independent discovery. API31 member names, constructors and ordering are not carried forward. Target SDK 36 and OEM non-SDK enforcement can validly produce `FAILED_CLOSED`.

## Stable concepts

System-approved Host ActivityInfo/Intent/token/task remain authoritative. Client transaction delivery precedes Activity-specific Context creation, object construction, attach and lifecycle. Guest logical state remains separate.

## Version-sensitive internals

ActivityClientRecord fields, transaction item representation, executor ordering, launch signatures, configuration/display/back state, non-SDK accessibility and OEM modifications require per-version handling.

## P0/P1/P2 boundaries

- P0 observes type shape and ordering without transaction mutation, Guest construction or attach. This is the only recommended next experiment.
- P1 tests a pre-attach class/classloader selection point with mandatory Host fallback. It is not authorized.
- P2 prepares Activity Context, Application, ActivityInfo, configuration, Window and atomic rollback for attach/substitution. It is deferred and not authorized.

## Access mechanism comparison

Debug-only reflection or equivalent non-mutating transaction/executor observation is the smallest P0 candidate; access denial is a valid result. Hook libraries, custom Instrumentation, Handler replacement, Root/Xposed and production contamination are rejected for P0. JVMTI and AOSP-instrumented builds remain alternate research tools.

## Failure/rollback

P0 aborts on API mismatch, access denial, Host lifecycle/token/window regression, crash/ANR, Guest installation/lifecycle, missing fallback or ambiguous evidence. P0 never mutates state. P1 must reject before mutation whenever possible; mutation rollback is conditional. `Activity.attach` is the irreversible boundary and is prohibited.

## Device matrix

Future P0 runs separately on API31 Xiaomi Mi 10 and API36 emulator-5554, collecting device/runtime identity, APK/revision hashes, Host carrier observations, internal type/order evidence, fallback, Guest install state, access exceptions, logcat and dumpsys. No internal observation code was run in this task.

## Security/release boundary

Future code remains debug-only and absent from Release. Target SDK 36 non-SDK restrictions are not bypassed. Binder, Hook, Root/Xposed, native Binder and seccomp are not needed for P0. API/OEM access failure produces `FAILED_CLOSED`.

## Preferred next experiment

`ACT-004B-P0 observation-only internal surface probe` produces independent reachability and runtime-shape evidence without risking class selection or attach. P0 success authorizes only a new review, not P1/P2.

## Deferred work

P1 class/classloader selection, Guest ActivityInfo/parser completeness, Guest Application/Context contract, attach atomicity, Window/back/configuration behavior, process death, saved state, result routing and P2 substitution remain deferred.

## Scope compliance

Only docs were modified. No source, build output, Gradle dependency, raw task18-task24 evidence or device state changed. No positive experiment ran and no L3 claim was created.

## Modified docs

```text
docs/experiments/TASK-24-ACT004A-NEGATIVE-RESULT.md
docs/research/activity/04_API31_SUBSTITUTION_SURFACE.md
docs/research/activity/05_API36_SUBSTITUTION_SURFACE.md
docs/research/activity/SOURCES.md
docs/design/ACTIVITY_CLIENT_ADAPTER_PLAN.md
docs/experiments/TASK-25-ACT004A-POSITIVE-PREFLIGHT-RESULT.md
```

## Git status

The independent docs commit excludes untracked Codex task input files.

## Conclusion

```text
ACT-004A-NEGATIVE = CONFIRMED
Positive preflight/design = READY
Positive substitution implemented = NO
Guest Activity attached = NO
Guest lifecycle executed = NO
L3 = NOT CONFIRMED
Host token/window ownership = HOST
Recommended next experiment = ACT-004B-P0 observation-only
P1 pre-attach selection authorized = NO
P2 attach/substitution authorized = NO
```
