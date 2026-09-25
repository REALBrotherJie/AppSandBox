# TASK-22 ACT-003 Manifest Stub Baseline Result

日期：2026-09-25

## Question and hypothesis

ACT-003 asked whether one debug-manifest Host standard Activity, launched through the normal Android path, receives a real Host Activity task, Window and publicly observable non-null window token on API31 and API36.

The hypothesis was confirmed. All observed system records belong to the Host Stub, not to the Guest.

## Scope

This task added one debug-only standard `Act003StubActivity`. It did not instantiate `GuestMainActivity`, call `Activity.attach`, substitute a Guest object, create a Stub pool, map launch modes, run Guest lifecycle, replace Instrumentation, intercept ActivityThread/ClientTransaction/Handler/Binder, or use hidden API, internal reflection, Hook, native Binder or seccomp. No production `app/src/main` runtime changed.

## Stub manifest

```text
component=com.example.appsandbox/.experiments.act003.Act003StubActivity
exported=false
launchMode=standard
theme=@style/Theme.AppSandbox
process=Host default
taskAffinity=Host default
```

Only primitive/String extras are accepted: `launchId`, logical Guest package/component and revision ID. No Guest Parcelable, ActivityInfo or Binder token crosses the launch Intent.

## Host lifecycle and public state

Both APIs observed the system-driven order:

```text
onCreate -> onStart -> onResume -> onWindowFocusChanged(true)
```

The invalid launch path observed `onCreate -> finish -> onDestroy`. Lifecycle methods were never called manually.

Valid Stub observations on both APIs:

```text
packageName=com.example.appsandbox
componentName=com.example.appsandbox/.experiments.act003.Act003StubActivity
isTaskRoot=false
applicationNonNull=true
baseContextNonNull=true
intent.action=com.example.appsandbox.debug.ACT003
window.class=com.android.internal.policy.PhoneWindow
window.decorView.class=com.android.internal.policy.DecorView
window.attributes.type=2
decor.windowToken.nonNull=true
decor.applicationWindowToken.nonNull=true
hostStubTokenOnly=true
```

Token values and Binder internals were not printed, reflected or compared.

## API31

```text
serial=7b670025
API=31
ABI=arm64-v8a,armeabi-v7a,armeabi
pageSize=4096
revisionId=ad233f61-b081-454b-ad57-f3287f56a7b6
SHA256=6b3c0d1758be4f01156a7e74620614f09add22412f088ebfaa6a3fe209a7ade3
verification=VALID
taskId=51
decor.windowToken.nonNull=true
decor.applicationWindowToken.nonNull=true
```

`dumpsys activity` reports `mResumedActivity` and `ResumedActivity` as the Host `Act003StubActivity` in task 51. `dumpsys window` reports its focused Host Window and Host ActivityRecord.

## API36

```text
serial=emulator-5554
API=36
ABI=x86_64,arm64-v8a
pageSize=4096
revisionId=92acf2c3-51ba-47a8-945d-5fcd9fa2a4af
SHA256=6b3c0d1758be4f01156a7e74620614f09add22412f088ebfaa6a3fe209a7ade3
verification=VALID
taskId=10
decor.windowToken.nonNull=true
decor.applicationWindowToken.nonNull=true
```

`dumpsys activity` reports `topResumedActivity` and `ResumedActivity` as the Host `Act003StubActivity` in task 10. `dumpsys window` reports its focused Host Window and Host ActivityRecord.

## Invalid launchId

On both APIs, the internal debug launch without `launchId` failed closed:

```text
launchResult=INVALID_LAUNCH_ID
guestActivityInstantiated=false
hostSurvived=true
finish/onDestroy observed
```

The Stub remains `exported=false` and cannot be used as an external entry point.

## Guest absence and direct launch

On both APIs:

```text
guestActivityInstantiated=false
Guest package pm path before/after=empty
directGuestLaunch=REJECTED
directGuestExceptionClass=android.content.ActivityNotFoundException
hostSurvived=true
```

ACT-003 never creates a Guest ClassLoader or reads the Guest Activity construction counter. The production GuestStore revision is used only as verified logical mapping metadata.

## L0-L5 classification

```text
L0 CONFIRMED by ACT-001
L2 Java object construction observed by ACT-002
L3 NOT CONFIRMED; ACT-003 confirms only a real Host Stub carrier baseline
L4 NOT TESTED
L5 NOT AVAILABLE
```

## Scope compliance

```text
Guest Activity instantiated = NO
Guest Activity attached = NO
Guest lifecycle executed = NO
Host lifecycle system-driven = YES
Host Stub token/task/window observed = YES
Production Activity runtime change = NO
Instrumentation replacement = NO
ActivityThread/ClientTransaction interception = NO
Binder interception = NO
hidden API = NO
Hook/native/seccomp = NO
```

## Build and release boundary

The required commands passed:

```text
:app:testDebugUnitTest
:app:assembleDebug
:app:assembleRelease
:test-guests:GuestTestApp:assembleDebug
git diff --check
```

Release manifest inspection contains no `ExperimentActivity`, `Act001Runner`, `Act002Runner`, `Act003StubActivity`, `act001`, `act002`, `act003` or task22 debug entry.

## Modified files

```text
app/src/debug/AndroidManifest.xml
app/src/debug/java/com/example/appsandbox/experiments/act003/Act003Runner.kt
app/src/debug/java/com/example/appsandbox/experiments/act003/Act003StubActivity.kt
app/src/debug/java/com/example/appsandbox/experiments/v1/ExperimentActivity.kt
scripts/task22-run.ps1
docs/Codex/task-22.txt
docs/experiments/TASK-22-ACT003-MANIFEST-STUB-BASELINE-RESULT.md
docs/experiments/evidence/task22/7b670025/
docs/experiments/evidence/task22/emulator-5554/
```

## Git and conclusion

Checkpoint commit: `47727d6 test(activity): confirm guest view and object baselines`.

```text
ACT-003 = CONFIRMED
Guest Activity instantiated = NO
Guest Activity attached = NO
Guest lifecycle executed = NO
Host Stub token/task/window baseline = CONFIRMED
Ready for ACT-004 Stub transaction to Guest Activity substitution design = YES
```

ACT-004 substitution is not implemented by this task.
