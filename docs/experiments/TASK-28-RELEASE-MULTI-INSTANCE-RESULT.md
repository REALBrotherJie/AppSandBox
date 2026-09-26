# TASK-28 Release Multi-Instance Result

日期：2026-09-26

## Milestone

Release now exposes a supported Guest View workspace. The visible component is always Host `GuestWorkspaceActivity`; Guest Activity/Application lifecycle, attach, ActivityThread, Binder interception, hidden APIs and hooks are not used.

## Supported contract

Guest metadata declares contract version `1` and a layout resource name. Host code inflates only that declared layout with Guest resources and a per-instance context. Invalid metadata, missing layout or missing revision fails closed with a user-visible unsupported/corrupt state. This is not arbitrary APK compatibility.

## Store risk coverage

`GuestInstanceRegistry` uses injectable file operations, synchronized per-root transactions, durable temp writes, backup recovery and replacement moves. Tests cover normal CRUD, atomic write failure, malformed primary plus valid backup, double corruption, unknown schema, duplicate IDs, `../` and symlink escape, concurrent different IDs, concurrent same ID, delete failure/retry, and preservation of B/Guest artifact references.

## Release workflow

Main Activity can create, list, open and delete instances for the current imported supported Guest revision. Workspace state is stored below each instance data root and survives Activity/process restart. Release manifest contains `GuestWorkspaceActivity` with `exported=false`; debug experiment activities are excluded.

## Device validation

| API | Serial | Result |
| --- | --- | --- |
| 31 | `7b670025` | PASS: app launch, Guest APK staged, Guest package absent |
| 36 | `emulator-5554` | PASS: app launch, Guest APK staged, Guest package absent |

Raw outputs are ignored under `build/reports/task28/<serial>/`; no new tracked dumps, logs or screenshots are included. Full picker-driven A/B interaction was not automated in this run; the release source path and JVM Store risk tests are the completion gates.

## Quality gate

Unit tests, debug/release builds, Guest APK build and `git diff --check` passed before the focused commit. Known limitation: the project emits an unsigned release APK, so device launch used the debug-signed APK while release manifest/build validation used the release variant. Importing through the Android document picker remains a platform UI step rather than a hidden/debug trigger.
