# Task-48 Logical Mainline

Date: 2026-09-27  
Status: PARTIALLY CONFIRMED

## Completed

- Corrected intent fixture Activity revision-only filters to include `android.intent.category.DEFAULT` while preserving custom categories and Receiver semantics.
- Repaired shared bounded ADB helper to execute an explicit process with hard timeout and process-local exit code, stdout/stderr capture, and stable `ADB_TIMEOUT`/`ADB_EXIT_<code>` failures.
- Task-42 and Task-43 ADB command paths now use the shared bounded helper; PowerShell syntax parsing passed.
- JVM, `testDebugUnitTest`, `assembleDebug`, and `assembleRelease` passed with the canonical SDK.

## Device execution

- API31 `7b670025` was online and reached the app internal `STARTED -> PASS` resolver reports, but Task-43 business assertions failed for VIEW ambiguity, EDIT resolution, disabled, and permission cases.
- API36 and the complete Task-42/framework parity/migration matrix were not confirmed after the runner defect was found.
- No Guest package was installed and no Activity attach or lifecycle interception work was started.

## Gate

Main was not modified. The branch remains below Task-48 acceptance because resolver/device matrix failures remain and framework parity was not completed.
