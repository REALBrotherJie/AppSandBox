# Task-51/53 ACT-006 API31 Controlled Activity Attach

Date: 2026-09-27
Status: REJECTED

## Corrected result

- The earlier `861e509` 7/7 claim was invalid: its valid report contained `MISSING_CLASS` while the script still wrote PASS.
- The corrected runner imports the fixture through `GuestStore`, creates a real instance, re-reads both registries, hashes the committed read-only `base.apk`, and validates its declared Activity.
- The Guest Activity class was loaded from the committed APK by an independent `DexClassLoader`; Host classpath substitution is rejected.
- API31 `Activity.attach` is not exposed by device reflection on `7b670025`. The adapter therefore returned `ACCESS_DENIED` before resolving hidden Host fields, constructing the Guest, or invoking attach.
- The authorized path is wired to the process-singleton `Act006StateMachine` and an API31 executor. Capability preparation gates that state machine, so this device run did not enter it; common attempted/completed counters do not claim a hidden-method invocation.
- No bypass was attempted. Guest lifecycle, view calls, Instrumentation replacement, transaction/Binder mutation, Hook/native/JVMTI remain absent.

## Device matrix

- Device: API31 `7b670025` only.
- Valid: `classLoaded=true`, `guestConstructed=false`, `attachInvokeAttempted=false`, `attachCompleted=false`, `guestLifecycle=false`.
- Valid was repeated with a new run ID and a verified new process; both runs produced the same fail-closed result.
- Stale revision, artifact mismatch, missing class, non-Activity, wrong fingerprint, access denial, and duplicate launch ID produced their expected rejection reasons with zero construction/invocation.
- Reports use unique run/case IDs and one atomically renamed `status=FINAL` result.
- Guest package absence and Guest ActivityRecord absence were queried from the device rather than hard-coded.
- Recovery force-stopped the experiment process, started Host `MainActivity`, observed a new PID, and confirmed Host resumed state through `dumpsys activity`.

## Gates

- Focused `testDebugUnitTest` and debug assembly passed.
- Task-49 API31 smoke passed.
- Debug/release assembly, release exclusion inspection, and `git diff --check` passed.
- Raw output remains under ignored `build/reports/task51/`.

## Conclusion

API31 attach call/result = not invoked; device reflection access unavailable
Guest constructed/attached/lifecycle = false / false / false
Host task/window/token/ActivityRecord = Host ownership unchanged; no Guest ActivityRecord
failure recovery = new PID and resumed Host verified
negative cases = 7 passed, zero attach attempts
pushed = false
ready for integration review = YES, as an unavailable/fail-closed result
