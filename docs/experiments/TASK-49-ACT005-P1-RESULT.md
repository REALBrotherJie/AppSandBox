# Task-49 ACT-005 P1 Pre-attach Class Selection

Date: 2026-09-27
Status: CONFIRMED

## Result

- Added debug-only, version-specific API31 and API36 declaration probes. Each records a runtime declaration fingerprint and capability/access outcome after the Host Stub has entered `onCreate`.
- The runner accepts only a verified instance/revision/artifact SHA/component mapping and uses a dedicated Guest `DexClassLoader` to load and verify the Activity class without invoking its constructor.
- `CLASS_SELECTED -> ROLLED_BACK_TO_HOST` names only the runner's local bookkeeping path. It did not modify framework class selection or a launch transaction and therefore is not evidence of a real framework replacement or rollback. The system-created ACT-003 Stub remained the only Activity object, task, window, token and lifecycle owner.
- Empty/stale mappings, artifact mismatch, stale component, missing/non-Activity class, API mismatch, access denial and conflicting repeated launch IDs fail closed. Repeating the same launch after process restart remains recoverable.

## Device matrix

- API31 `7b670025`: 11/11 cases passed; adapter fingerprinted `ActivityThread.ActivityClientRecord`; valid class selection and Host rollback passed.
- API36 `emulator-5554`: 11/11 cases passed after using its independent `LaunchActivityItem` `mInfo`/`mIntent` shape; valid class selection and Host rollback passed.
- Both devices observed ACT-003 `onCreate -> onStart -> onResume`, non-null Host window/application tokens, Host survival, no Guest package installation and no Guest ActivityRecord.
- Guest object constructed=false, attach=false, lifecycle=false on every path.

## Regression and boundary

- Task-42 recovery, Task-43 resolver, framework parity and logical adapter/stale/migration smoke passed on API31/API36.
- Full JVM, Debug, Release, fixture and release-manifest gates passed; Release contains no Task-49 adapter or runner.
- No `Activity.attach`, Guest construction/lifecycle, Instrumentation replacement, transaction mutation, Binder/Hook/native work or P2 substitution was performed.
