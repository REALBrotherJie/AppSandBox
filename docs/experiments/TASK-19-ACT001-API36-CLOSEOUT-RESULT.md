# TASK-19 ACT-001 API36 Closeout Result

日期：2026-09-24

## Status

```text
ACT-001 = PARTIALLY CONFIRMED
blocking reason = API36 device unavailable in this execution environment
```

API31 L0 remains confirmed. API36 was attempted without fabricating evidence, but the only connected device was API31.

## Report path issue

Before this task, `ExperimentActivity.onCreate(state != null)` always read `files/task15-$mode.txt`. For `mode=act001`, the completed report was written to `files/task18-act001.txt`, so a recreated Activity displayed `No completed experiment`.

The minimal debug-only fix centralizes report naming in `reportFile(mode)`. `act001` now reads `task18-act001.txt`; historical task15/task16 modes retain their previous names. A debug-only delayed `Activity.recreate()` check writes `task19-recreation.txt` only after the `state != null` branch reads the report.

## State restoration result

API31 controlled recreation evidence:

```text
stateRestored=true
report=task18-act001.txt
reportContainsConclusion=true
```

This verifies report-path restoration only. It is not Guest Activity lifecycle testing.

## API36 device availability

An API36 AVD named `Pixel_3a_API_36_extension_level_19_x86_64` exists locally and was started, but it did not become visible to ADB during this run. The connected inventory remained:

```text
7b670025 device product=umi model=Mi_10 API=31
```

Running `scripts/task19-run.ps1 -Serial 7b670025 -RequireApi36` stopped with the explicit blocker `API36 required but connected device is API 31`. No API36 result is claimed.

## API31 production ACT-001 evidence

Device: Xiaomi Mi 10, serial `7b670025`, API31.

```text
revisionId=29c0eed8-dd4b-4a1c-a54b-0bb671818972
sha256=057ad5469a30a49ad48309a2a29d69a8f6810ca9f7c2c80b943c1a22957f4c00
fileSize=31105
verification=VALID
guest.markerPresent=true
guest.view.contextIsControlled=true
guest.view.resourcesPackage=com.example.appsandbox.testguest
host.package=com.example.appsandbox
host.component=com.example.appsandbox/.experiments.v1.ExperimentActivity
host.taskId=47
host.window.class=com.android.internal.policy.PhoneWindow
guest.systemInstalled=false
directGuestLaunch=REJECTED
directGuestExceptionClass=android.content.ActivityNotFoundException
hostSurvived=true
conclusion=ACT-001_CONFIRMED_L0
```

Evidence is under `docs/experiments/evidence/task19/7b670025/`. The Guest `pm path` output is empty before/after. The APK was imported through production GuestStore and verified before the `DexClassLoader` and resource objects were created.

## API36 evidence

```text
API36 device available = NO
API36 serial = none
API36 verification = NOT EXECUTED
API36 marker = NOT EXECUTED
API36 guest installed = NOT EXECUTED
API36 direct launch = NOT EXECUTED
API36 host survived = NOT EXECUTED
```

## L0-L5 classification

```text
L0 API31 CONFIRMED; API36 PENDING
L1 NOT TESTED / NOT CLAIMED
L2 NOT TESTED / NOT CLAIMED
L3 NOT TESTED / NOT CLAIMED
L4 NOT TESTED / NOT CLAIMED
L5 NOT AVAILABLE
```

## Scope compliance

```text
production Activity runtime change = NO
Guest Activity instantiation = NO
Activity.attach = NO
Stub Activity = NO
Instrumentation replacement = NO
ActivityThread/ClientTransaction interception = NO
Binder interception = NO
hidden API = NO
Hook/native/seccomp = NO
```

## Build and regression

The required build commands passed:

```text
:app:testDebugUnitTest
:app:assembleDebug
:app:assembleRelease
:test-guests:GuestTestApp:assembleDebug
```

`git diff --check` passes. Release merged manifest contains no debug `ExperimentActivity`, `Act001Runner`, `act001`, task18 or task19 entry.

## Modified files

```text
app/src/debug/java/com/example/appsandbox/experiments/v1/ExperimentActivity.kt
scripts/task19-run.ps1
docs/experiments/TASK-19-ACT001-API36-CLOSEOUT-RESULT.md
docs/experiments/evidence/task19/7b670025/device.txt
docs/experiments/evidence/task19/7b670025/act001-api36.txt
docs/experiments/evidence/task19/7b670025/act001-api36-logcat.txt
docs/experiments/evidence/task19/7b670025/act001-api36-pm-path.txt
docs/experiments/evidence/task19/7b670025/recreation.txt
docs/experiments/evidence/task19/7b670025/api36-unavailable.txt
```

The task-18 files remain part of the current uncommitted worktree and were not rewritten by task-19. No `app/src/main` production Activity runtime code changed.

## Conclusion

```text
Activity runtime implemented = NO
Guest Activity object instantiated = NO
Stub/Hook/Binder/hidden API used = NO
Ready for ACT-002 Guest Activity Java object construction: NO
```

ACT-002 remains unauthorized until API36 ACT-001 evidence is obtained and both API31/API36 L0 results pass.
