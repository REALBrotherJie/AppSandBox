# TASK-29 Release Import Workflow Result

日期：2026-09-26

## Milestone

The supported Guest product path is now automatically reproducible: import a real GuestTestApp APK, create two instances, operate isolated counters, restart, delete A and continue B. The workflow uses product import/store/workspace code and never writes registry or counter files through adb.

## Import state flow

`GuestImportSession` models Empty, Selecting, Importing, Success, Failure and Unsupported. Cancel/failure/unsupported states do not overwrite the last valid revision. Process recreation restores the latest verified `GuestStore` record. UI messages distinguish unreadable/invalid input from unsupported Guest contract.

## Contract and workspace

Only metadata contract version 1 with a declared layout can be imported. Workspace resolves only `instanceId`, verifies the Guest artifact SHA before enabling state operations, and fails closed for missing instances, registry corruption or missing/changed revisions. Guest Activity/Application lifecycle is not executed.

## Device results

| API | Serial | Assertions | Result |
| --- | --- | --- | --- |
| 31 | `7b670025` | import, A/B create, 2/3 counters, restart, delete A, B continue, Guest not installed | PASS |
| 36 | `emulator-5554` | import, A/B create, 2/3 counters, restart, delete A, B continue, Guest not installed | PASS |

Raw output is ignored under `build/reports/task29/<serial>/`; no dumps, screenshots or per-device evidence are tracked.

## Automation and release boundary

Debug adds `Task29AutomationActivity` to feed only an app-private staged APK into `GuestImportCoordinator`; all later actions use actual UI controls. Release merged manifest does not contain this seam or task27 experiment entries. `GuestWorkspaceActivity` remains `exported=false`.

## Signing

Gradle has no release signing configuration and produces `app-release-unsigned.apk`. Device validation therefore installs the debug-signed APK. Release validation is limited to successful build, unsigned artifact inspection and merged-manifest checks; no release installation is claimed.

## Quality gate

JVM tests cover import cancel/failure/unsupported distinctions, previous-success retention, recreation restoration, plus the task-28 persistence failure matrix. Debug/release/Guest builds and `git diff --check` pass.

Known limitation: the supported contract currently exposes one declared Guest layout and Host-managed instance state; it does not provide arbitrary APK or Guest component virtualization.
