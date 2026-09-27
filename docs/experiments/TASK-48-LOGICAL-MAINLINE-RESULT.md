# Task-48 Logical Mainline

Date: 2026-09-27  
Status: CONFIRMED

## Completed

- Corrected every implicit Activity fixture filter to include Android `DEFAULT`, while preserving custom categories and Receiver semantics.
- Repaired shared bounded ADB helper to execute an explicit process with hard timeout and process-local exit code, stdout/stderr capture, and stable `ADB_TIMEOUT`/`ADB_EXIT_<code>` failures.
- Task-42 and Task-43 ADB command paths now use the shared bounded helper; PowerShell syntax parsing passed.
- Framework scheme/host matching now preserves Android case-sensitive semantics; the supported parity set is 15/15 with zero mismatch on both APIs.
- Schema-3 Guest component registries migrate atomically to schema 4 while preserving revision, artifact SHA, component identity and data; schema 2 and corrupt/unknown schemas remain fail-closed.
- JVM, Debug, Release, release-manifest and diff checks passed with the canonical SDK.

## Device execution

- API31 `7b670025`: Task-42 recovery, explicit resolver, implicit resolver, framework parity and logical adapter/stale/migration matrix passed.
- API36 `emulator-5554`: the same matrix passed; writable DCL negative produced `SecurityException`.
- Task-43 passed 9/9 cases on both devices. Ambiguous decisions never produced a plan; duplicate operation identity and stale plans failed closed.
- Both devices kept Guest packages uninstalled and no Guest ActivityRecord was observed. No Activity attach or lifecycle interception work was started.

## Gate

All Task-48 gates passed. The integration commit is eligible for the conditional local main merge; no push is performed.
