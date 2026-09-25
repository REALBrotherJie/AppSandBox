# TASK-18 ACT-001 Host Activity + Guest View Result

日期：2026-09-24  
实验等级：L0 only

## Question

Can the Host Activity load a production immutable GuestStore revision, inflate a Guest layout/theme/resource, display it, and still remain the system-owned Host Activity? What happens when the uninstalled Guest Activity is launched directly?

## Hypothesis

Guest layout/theme/resources can render through the existing Controlled Context. The View may retain Guest Context/Resources, but no Guest Activity, token, ActivityRecord or Guest task identity is created. Direct launch of the uninstalled Guest Activity is rejected and the Host survives.

## Scope and non-goals

ACT-001 only. No Stub Activity, Guest Activity hosting, `Activity.attach`, Instrumentation replacement, ActivityThread/ClientTransaction/Handler interception, Binder interception, hidden API, reflection, Hook, native or seccomp. This is not L1-L5 Activity runtime.

## Implementation

Added debug-only `Act001Runner`. It reads `GuestStore.latestRecord()`, verifies with `GuestArtifactVerifier` before creating `DexClassLoader` or Guest resources, creates the existing `Exp003c1ControlledContext`, inflates `exp003_themed_layout`, inserts it into Host content, records public Host identity, and performs direct Guest launch negative control.

## Guest input

Guest APK contains the existing `exp003_themed_layout`, Guest marker string/theme, and a manifest-only `.runtime.GuestMainActivity` declaration used only for direct uninstalled-component negative control. The Guest APK is never installed.

## Expected evidence fields

```text
verification=VALID
guest.markerPresent=true
guest.view.contextIsControlled=true
guest.child.contextIsControlled=true
guest.systemInstalled=false
directGuestLaunch=REJECTED
hostSurvived=true
conclusion=ACT-001_CONFIRMED_L0
```

The actual API31/API36 reports belong under `docs/experiments/evidence/task18/<serial>/` and must contain the recorded revision SHA, marker, Host package/component/taskId/window class, direct-launch result and `pm path` absence.

## API31 result

Device: Xiaomi Mi 10 (`7b670025`), API31.

```text
revisionId=79819198-994d-4d25-9b69-57986b61b068
sha256=057ad5469a30a49ad48309a2a29d69a8f6810ca9f7c2c80b943c1a22957f4c00
fileSize=31105
verification=VALID
markerPresent=true
guest.view.contextIsControlled=true
guest.child.contextIsControlled=true
guest.view.contextPackage=com.example.appsandbox.testguest
guest.view.resourcesPackage=com.example.appsandbox.testguest
host.package=com.example.appsandbox
host.component=com.example.appsandbox/.experiments.v1.ExperimentActivity
host.taskId=43
host.window.class=com.android.internal.policy.PhoneWindow
guest.systemInstalled=false
directGuestLaunch=REJECTED
directGuestExceptionClass=android.content.ActivityNotFoundException
hostSurvived=true
conclusion=ACT-001_CONFIRMED_L0
```

The report is stored at `docs/experiments/evidence/task18/7b670025/act001.txt`. `act001-pm-path.txt` is empty, confirming the Guest package remains uninstalled.

## API36 result

Not executed. On 2026-09-24 the connected ADB inventory contained only API31 device `7b670025`; no API36 emulator/device was available. No API36 result is claimed or synthesized.

## L0-L5 classification

```text
L0 CONFIRMED
L1 NOT TESTED / NOT CLAIMED
L2 NOT TESTED / NOT CLAIMED
L3 NOT TESTED / NOT CLAIMED
L4 NOT TESTED / NOT CLAIMED
L5 NOT AVAILABLE
```

## Security boundary

The Guest APK remains an immutable, verified code/resource source. The system sees only `com.example.appsandbox` Host Activity. The Guest package is not installed and does not own a system Activity/task/window token. SHA-256 proves artifact bytes match the registry; it is not publisher authenticity or malware detection.

## Known limitations

ACT-001 does not test Guest Activity Java construction, Activity Context, lifecycle translation, Stub mapping, task/back stack, launchMode, result routing, process death, saved state, split resources, permissions or multi-window. Public `taskId` and `Window` observations are Host observations only.

## Build/test commands

All required commands passed:

```text
:app:testDebugUnitTest
:app:assembleDebug
:app:assembleRelease
:test-guests:GuestTestApp:assembleDebug
```

Release merged manifest verification produced no `ExperimentActivity`, `Act001Runner` or `act001` matches. The debug-only entry is excluded from release.

## Modified files

```text
app/src/debug/java/com/example/appsandbox/experiments/act001/Act001Runner.kt
app/src/debug/java/com/example/appsandbox/experiments/v1/ExperimentActivity.kt
test-guests/GuestTestApp/src/main/AndroidManifest.xml
scripts/task18-run.ps1
docs/experiments/TASK-18-ACT001-HOST-GUEST-VIEW-RESULT.md
docs/experiments/evidence/task18/7b670025/device.txt
docs/experiments/evidence/task18/7b670025/act001.txt
docs/experiments/evidence/task18/7b670025/act001-logcat.txt
docs/experiments/evidence/task18/7b670025/act001-pm-path.txt
```

No `app/src/main` production Activity runtime code was modified.

## Git status

The task-18 changes are currently uncommitted. `git diff --check` passes; build outputs are not part of the intended change set.

## Conclusion

```text
ACT-001 = PARTIALLY CONFIRMED
Activity runtime implemented = NO
Guest Activity object instantiated = NO
Stub/Hook/Binder/hidden API used = NO
```

## Next step

`Ready for ACT-002 Guest Activity Java object construction: NO` because API36 evidence is still missing. ACT-002 is not implemented by this task.
