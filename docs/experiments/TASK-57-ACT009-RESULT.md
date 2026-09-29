# Task-57 ACT-009 Host-carrier Activity subset

Date: 2026-09-30
Status: CONFIRMED

## Completed milestone

- Added resolver-gated logical Guest Activity launch inside a Host-owned
  `GuestActivityCarrierActivity`.
- Carrier renders the supported contract-v2 Guest view/actions through the
  existing Host/runtime boundary.
- Persisted logical Activity identity is bound to instance, revision, package,
  APK SHA and component. Back, explicit result, reopen and stale launch
  rejection are implemented.
- Added fail-closed malformed/non-exported/unknown component handling and
  closed-launch protection.
- Hardened state persistence with staged atomic replacement, strict schema and
  tombstone validation, bounded history and rollback-focused tests.
- API31 compatibility fix keeps API33 back-dispatch classes out of the
  pre-33 class initialization path.

## Device matrix

- API31 `7b670025`: PASS, immutable final APK run
  `build/reports/task57/7b670025/0d137fb01bd64f009f894eaba93d7a08/`.
- API36 `emulator-5554`: PASS, immutable final APK run
  `build/reports/task57/emulator-5554/20a456b876ee45a1a89c041c8a575b1d/`.
- Both runs passed resolver negatives, carrier identity/action, result/back,
  reopen correlation, recreation, Host restart, runtime-only recovery,
  malformed fail-closed, active-delete rejection, stopped-delete isolation
  and A/B isolation.
- Both runs confirmed Guest package uninstalled and no Guest ActivityRecord.

## Scope and remaining boundary

- No `Activity.attach`, hidden API, ActivityThread/ClientTransaction mutation,
  Binder interception, hook/native/root path or Guest Activity lifecycle.
- This is a logical Host-carrier subset, not Android Activity virtualization:
  Guest Activity Java lifecycle, attach/token/window/task ownership and
  framework transaction substitution remain unimplemented by design.
- API36 Act008 client instrumentation: 6/6 PASS. API31 instrumentation APK
  installation was blocked by device policy (`INSTALL_FAILED_USER_RESTRICTED`);
  the dual-device product matrix remains valid.
- Logical closed-history is bounded at 2048 records. Release APK is unsigned;
  device runs use the debug-signed APK.

## Build

- `:app:testDebugUnitTest`, `:app:assembleDebug`,
  `:app:assembleDebugAndroidTest`, `:app:assembleRelease`,
  `:test-guests:GuestTestApp:assembleNormalDebug` and `git diff --check`: PASS.
- Release manifest excludes Task-57 debug automation.
