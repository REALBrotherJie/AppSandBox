# Task-52 ACT-006 API36 Controlled Attach

Date: 2026-09-27
Status: PARTIALLY CONFIRMED

- Added debug-only API36 adapter and Host Stub route. The adapter fingerprints the runtime `Activity.attach` declaration and supplies only Host carrier values.
- Guest construction is attempted only after API/SHA/class checks. No Guest lifecycle, `setContentView`, finish, startActivity, callback, transaction mutation, Binder interception, hook, native or root path is used.
- Attach failure is classified `PROCESS_RECOVERY_REQUIRED`; the runner force-stops and restarts the Host process and checks Host fallback.
- Negative cases include API mismatch, SHA mismatch, stale/missing mapping, non-Activity class, access denial and duplicate launch handling in the adapter/test boundary.
- Writable-DCL remains a negative control and must report `SecurityException`; no writable optimized-directory workaround is used.
- Device target is API36 `emulator-5554` only. No Guest package install or Guest ActivityRecord is claimed.

Build execution was blocked in this environment before compilation because Android SDK licenses for `build-tools;35.0.0` and `platforms;android-36` are not accepted. Device and full gate therefore remain unverified in this checkout.
