# TASK-20 ACT-001 API36 Result

日期：2026-09-24

## Status

```text
ACT-001 = CONFIRMED
API31 L0 = CONFIRMED
API36 L0 = CONFIRMED
ACT-002 = NOT IMPLEMENTED
```

API31 evidence remains in `docs/experiments/evidence/task19/7b670025/`.

## AVD and API36 device

The existing `Pixel_3a_API_36_extension_level_19_x86_64` AVD was started with an isolated TEMP datadir. The first launch exposed `unknown skin name 'pixel_3a'`; task-20 added `-skin 1080x1920`. The second launch reached ADB without touching original userdata.

```text
emulatorPid=44808
serial=emulator-5554
boot/ADB=device
api=36
product=sdk_gphone64_x86_64
model=sdk_gphone64_x86_64
abi=x86_64,arm64-v8a
pageSize=4096
```

Diagnostics are in `docs/experiments/evidence/task20/api36-avd/`.

## API36 production ACT-001

Production GuestStore import and verification completed before DCL/resource creation:

```text
revisionId=113184b0-b871-4d91-b916-ad19ce5c7398
sha256=057ad5469a30a49ad48309a2a29d69a8f6810ca9f7c2c80b943c1a22957f4c00
fileSize=31105
verification=VALID
canRead=true
canWrite=false
guest.markerPresent=true
guest.view.contextIsControlled=true
guest.child.contextIsControlled=true
guest.view.resourcesPackage=com.example.appsandbox.testguest
```

The SHA matches the built GuestTestApp APK. The primary artifact was the production `GuestStore/base.apk`, not a task15/task18 readonly cache copy.

## API36 Host identity and negative control

```text
host.package=com.example.appsandbox
host.component=com.example.appsandbox/.experiments.v1.ExperimentActivity
taskId=8
window.class=com.android.internal.policy.PhoneWindow
guest.systemInstalled=false
directGuestLaunch=REJECTED
directGuestExceptionClass=android.content.ActivityNotFoundException
hostSurvived=true
```

Guest `pm path` was empty before and after the run. No Guest token or Guest task identity is claimed.

## Recreation

```text
stateRestored=true
report=task18-act001.txt
reportContainsConclusion=true
```

This validates the task-19 report-path fix only, not Guest Activity lifecycle.

## L0-L5

```text
L0 CONFIRMED on API31 and API36
L1 NOT TESTED / NOT CLAIMED
L2 NOT TESTED / NOT CLAIMED
L3 NOT TESTED / NOT CLAIMED
L4 NOT TESTED / NOT CLAIMED
L5 NOT AVAILABLE
```

## Scope compliance

```text
production Activity runtime change = NO
Guest Activity object instantiated = NO
Activity.attach = NO
Stub Activity = NO
Instrumentation replacement = NO
ActivityThread/ClientTransaction interception = NO
Binder interception = NO
hidden API = NO
Hook/native/seccomp = NO
```

## Build and files

The required commands passed:

```text
:app:testDebugUnitTest
:app:assembleDebug
:app:assembleRelease
:test-guests:GuestTestApp:assembleDebug
git diff --check
```

Release manifest inspection found no debug `ExperimentActivity`, `Act001Runner`, `act001`, task18 or task19 entry.

Task-20 files:

```text
scripts/task20-emulator.ps1
scripts/task20-run.ps1
docs/experiments/TASK-20-ACT001-API36-RESULT.md
docs/experiments/evidence/task20/api36-avd/
docs/experiments/evidence/task20/emulator-5554/
```

No `app/src/main` production runtime file was modified. Existing task-18/task-19 changes remain uncommitted in the shared worktree.

## Conclusion

```text
ACT-001 = CONFIRMED
Activity runtime implemented = NO
Guest Activity object instantiated = NO
Stub/Hook/Binder/hidden API used = NO
Ready for ACT-002 Guest Activity Java object construction: YES
```

This closes only ACT-001 L0 on API31/API36. It does not implement ACT-002, Stub Activity, `Activity.attach` or any high-compatibility carrier.
