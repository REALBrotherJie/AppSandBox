# EXP-001: Uninstalled APK Java Class Loading

Status: Planned, not executed.

## Question

Given an APK copied into AppSandbox private storage but not installed through PMS, can a supported Android class loader load one guest-defined ordinary Java/Kotlin class, create an object, and invoke a pure method?

## Hypothesis

`BaseDexClassLoader`/`DexClassLoader` can read an APK/dex path without the guest being installed. This would validate only Level 1, not Application, Resources, Activity, permissions, or system services.

## AOSP/API basis

Public `dalvik.system.BaseDexClassLoader` and `DexClassLoader` API contracts; ART/Dalvik dex loading behavior. Relevant conceptual source areas: `libcore/dalvik/src/main/java/dalvik/system/`, ART class-loader/runtime code, and the host application's private file APIs.

## Setup

1. Build `GuestTestApp` Phase A.
2. Select/import its APK through current Phase 1.
3. Confirm it is not installed as the guest package.
4. Run an isolated host-side test path with the copied `base.apk`.
5. Use a test class with a no-argument constructor and a pure method returning a constant.

## Host action

Create a loader with the copied APK path and an approved parent. Load the exact test class by name, instantiate it, invoke the pure method, and log loader identity, class name, result, and exceptions. Do not call `Context`, resources, Application, Binder, native code, or hidden APIs.

## Expected result

The class loads and returns the expected constant; the defining loader is not the host application loader; no guest package installation is observed.

## Failure modes

- `ClassNotFoundException`: archive/dex path or loader policy failure.
- `VerifyError`/`NoClassDefFoundError`: dependency or runtime class issue.
- access/linkage errors: class visibility or parent policy.
- process crash: unsafe code or runtime incompatibility; stop and analyze.
- package installation side effect: experiment invalid.

## Logs

Use `AppSandbox.Experiment001` with APK digest, API level, ABI, loader class, guest class, result, elapsed time, and exception stack. Never log guest secrets.

## Exit criteria

Pass requires three repeated runs on a defined device/API with the guest not installed and deterministic output. Failure blocks Resources/Application work and triggers an architecture review.

## Architecture impact

Pass confirms only the Code Runtime Level 1 hypothesis. It does not authorize production ClassLoader code or imply Context/Activity feasibility.
