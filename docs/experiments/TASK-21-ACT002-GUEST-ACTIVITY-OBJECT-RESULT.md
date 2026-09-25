# TASK-21 ACT-002 Guest Activity Java Object Result

日期：2026-09-25

## Question and hypothesis

ACT-002 asked whether an immutable, uninstalled Guest APK can provide an `Activity` subclass that is loadable and constructible as a plain Java object through a normal constructor and public `Instrumentation.newActivity`, without framework attach or lifecycle execution.

The hypothesis was confirmed on API31 and API36. Construction is possible, but the resulting object is not an attached Android Activity.

## Scope and non-goals

This task did not call `Activity.attach`, any lifecycle method, `setContentView`, Window setup, Stub Activity, Instrumentation replacement, ActivityThread/ClientTransaction/Binder interception, hidden API, reflection into internals, Hook, native Binder or seccomp. No production `app/src/main` runtime changed.

## GuestMainActivity test input

The Guest APK now contains:

```java
public final class GuestMainActivity extends android.app.Activity {
    public GuestMainActivity() {}
}
```

The actual class also exposes a static construction counter used only to prove that the two construction paths create separate objects. It does not override lifecycle methods, create Views, access services or start components.

## Production artifact verification

Both devices imported the built Guest APK through production GuestStore. `GuestArtifactVerifier` returned `VALID` before `DexClassLoader` creation.

```text
built SHA256=6b3c0d1758be4f01156a7e74620614f09add22412f088ebfaa6a3fe209a7ade3
fileSize=49300
artifact.canRead=true
artifact.canWrite=false
```

API31 revision: `cb2f0fca-48c3-4360-a527-e3401ae08517`.  
API36 revision: `639841a8-f447-4f65-b281-037baab3fc6a`.

## Class loading and assignability

On both APIs:

```text
classLoad=SUCCESS
guestClass=com.example.appsandbox.testguest.runtime.GuestMainActivity
guestClassLoaderIsGuest=true
guestLoaderDiffersFromHost=true
assignableToActivity=true
```

The missing-class control produced `ClassNotFoundException`. Casting `GuestProbe` to an Activity subclass produced `ClassCastException`.

## Construction paths

On API31 and API36:

```text
constructor=SUCCESS
instrumentation.newActivity=SUCCESS
objects.distinct=true
constructionCount=2
```

`Instrumentation.newActivity(ClassLoader, String, Intent)` is public and required no hidden replacement. The Instrumentation instance was only used as a public object factory.

## Unattached public property observations

Both construction paths produced the same observations on both APIs:

```text
getApplication=null
getWindow=null
getIntent=null
isFinishing=false
isDestroyed=false
getPackageName=NullPointerException because base Context is null
```

These values are evidence of an unattached Java object, not a usable Activity Context. No token, ActivityRecord, Task, Window or lifecycle is created.

## Direct Guest launch and system state

Direct explicit Guest launch remained rejected with `ActivityNotFoundException`. Before and after construction:

```text
guestInstalled=false
hostPackage=com.example.appsandbox
hostComponent=com.example.appsandbox/.experiments.v1.ExperimentActivity
hostTaskId unchanged=true
hostComponent unchanged=true
hostSurvived=true
```

API31 Host task ID was `49`; API36 Host task ID was `9`. These are Host observations only.

## API31

```text
serial=7b670025
API=31
ABI=arm64-v8a,armeabi-v7a,armeabi
pageSize=4096
class load=SUCCESS
assignableToActivity=true
constructor=SUCCESS
Instrumentation.newActivity=SUCCESS
```

Evidence: `docs/experiments/evidence/task21/7b670025/`.

## API36

```text
serial=emulator-5554
API=36
ABI=x86_64,arm64-v8a
pageSize=4096
class load=SUCCESS
assignableToActivity=true
constructor=SUCCESS
Instrumentation.newActivity=SUCCESS
```

Evidence: `docs/experiments/evidence/task21/emulator-5554/`.

## L0-L5 classification

```text
L0 CONFIRMED by ACT-001
L1 NOT TESTED / NOT CLAIMED
L2 Java object construction OBSERVED on API31/API36; not attached
L3 NOT TESTED
L4 NOT TESTED
L5 NOT AVAILABLE
```

## Scope compliance

```text
Guest Activity Java object constructed = YES
Guest Activity attached = NO
Guest lifecycle executed = NO
Guest Activity token/window/task = NOT CLAIMED
Production Activity runtime implemented = NO
Stub/Hook/Binder/hidden API used = NO
```

## Build and release boundary

The following passed:

```text
:app:testDebugUnitTest
:app:assembleDebug
:app:assembleRelease
:test-guests:GuestTestApp:assembleDebug
git diff --check
```

Release manifest inspection contains no `ExperimentActivity`, `Act001Runner`, `Act002Runner`, `act001`, `act002` or task21 debug entry.

## Modified files

```text
test-guests/GuestTestApp/src/main/java/com/example/appsandbox/testguest/runtime/GuestMainActivity.java
app/src/debug/java/com/example/appsandbox/experiments/act002/Act002Runner.kt
app/src/debug/java/com/example/appsandbox/experiments/v1/ExperimentActivity.kt
scripts/task21-run.ps1
docs/experiments/TASK-21-ACT002-GUEST-ACTIVITY-OBJECT-RESULT.md
docs/experiments/evidence/task21/7b670025/
docs/experiments/evidence/task21/emulator-5554/
```

The shared worktree still contains uncommitted task-18 through task-20 changes. No build output, emulator userdata or temporary APK is included in the intended change set.

## Conclusion

```text
ACT-002 = CONFIRMED
Guest Activity Java object constructed = YES
Guest Activity attached = NO
Guest lifecycle executed = NO
Guest Activity token/window/task = NOT CLAIMED
Production Activity runtime implemented = NO
Stub/Hook/Binder/hidden API used = NO
Ready for ACT-003 Manifest Stub token baseline = YES
```

ACT-003 is not implemented by this task.
