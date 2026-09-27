# Task-52/53 ACT-006 API36 Controlled Attach

Date: 2026-09-27
Status: REJECTED (platform access unavailable without prohibited bypass)

## Result

- Restored isolated API36 `emulator-5554`; no original AVD userdata was overwritten and adb server was not restarted.
- Imported GuestTestApp through production `GuestStore`, created a real instance, verified revision/component/file SHA and read-only `base.apk`, and selected the Guest class through an independent `DexClassLoader`.
- Adapter validates runtime API and artifact bytes. API36 attach arguments have explicit identities matching Android 36 source; repeated `int`/`IBinder` values are not guessed by type.
- Runtime reflection did not expose either Android 36 19/20-parameter `Activity.attach` overload. Both independent-process valid runs stopped before Guest construction with `ACCESS_DENIED`.
- Counts: constructor attempted/completed 0/0; attach invoked/completed 0/0; Guest lifecycle calls 0.
- No hidden-API bypass, Instrumentation replacement, transaction/Binder mutation, hook, native, root, Guest lifecycle, view, finish or callback was used.

## Matrix

- Stale revision, SHA mismatch, missing class, non-Activity class, API mismatch, forced access denial and duplicate launch ID: expected rejection with zero constructor/attach calls.
- Two valid run IDs in different processes: `ACCESS_DENIED` before construction; this is unavailable, not attach success.
- Force-stop/restart changed PID `6695 -> 6866`; new report matched its run ID and Host resumed.
- Device queries confirmed Guest package absent and no Guest ActivityRecord.
- Writable-DCL negative produced `SecurityException`; no writable optimized-directory workaround.
- Task-49 API36 smoke: 11/11 passed.

## Gates

- IntentFilterFixtures v1/v2 built first; `:app:testDebugUnitTest`: 113 passed, 1 skipped.
- `:app:assembleDebug`, `:app:assembleRelease`, release exclusion and `git diff --check`: passed.
- Raw reports remain ignored under `build/reports/task52/`.

Conclusion: API36 controlled attach is unavailable under the authorized access model. Host Stub remains the only system Activity/task/window/token owner.
