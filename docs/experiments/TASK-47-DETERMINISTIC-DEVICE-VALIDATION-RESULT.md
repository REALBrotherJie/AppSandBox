# Task-47 Deterministic Device Validation

Date: 2026-09-27  
Status: PARTIALLY CONFIRMED

## Completed

- Work remained on `codex/task45-integration` from `fb4d8e1`; main was not modified.
- Added a shared PowerShell helper for bounded ADB execution and `STARTED`/`PASS`/`FAIL` report records.
- Task-43 now accepts API31 and API36 serials, generates a unique runId, reads resolver results through `run-as` internal files, and records all case mismatches instead of relying on UIAutomator output.
- Debug resolver automation writes run-scoped internal result files.
- Debug/JVM/build verification passed with canonical SDK: `testDebugUnitTest`, `assembleDebug`, `assembleRelease`.

## Device findings

- API31 `7b670025` and API36 `emulator-5554` were both ADB online.
- Task-43 reached the app and produced deterministic reports on API31, but the imported fixture's first VIEW case returned `REJECTED reason=no-match` instead of the expected ambiguity. This is a device-side manifest/fixture parity defect, not an ADB hang; the runner records it as FAIL.
- Full Task-42 and framework IntentFilter parity matrix were not confirmed in this run.
- No Guest APK was installed as an Android package and no Guest Activity attach work was started.

## Gate status

`status=PARTIALLY CONFIRMED`; main integration is intentionally deferred until fixture/device parity and the remaining dual-device matrix pass.
