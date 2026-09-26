# TASK-35 Crash-Safe Interactive Guest State Result

Date: 2026-09-26

## Scope

Contract v2 counter state uses an instance-local `GuestStateStore` behind
`GuestViewSession`, preserving the `counter()` and `execute()` API. Writes are
serialized per instance with a JVM lock and lock file, forced to a temporary
file, then atomically replaced. A valid backup repairs a missing or corrupt
primary; dual corruption fails closed. Schema, range, SHA-256 checksum, and
symbolic-link validation reject invalid state and paths. Persistence failures
reach the binder failure path instead of silently resetting the counter.

State files live under `<instanceRoot>/files/counter.txt` with `.bak`, `.tmp`,
and `.lock` companions. Persistence failures reach the binder failure path
instead of silently resetting the counter.

## Verification

`GuestStateStoreTest` covers concurrent updates, reset/toggle contention,
backup repair, dual corruption, stale temporary files, symbolic-link rejection,
separate-root isolation, concurrent mixed actions, and failed temporary writes.
All 9 Store tests and the full `:app:testDebugUnitTest` suite pass.

`scripts/task35-run.ps1` writes fresh `RUNNING/FAIL/PASS` results and keeps raw
UI/task output under ignored `build/reports/task35/<serial>/`.

| API | Serial | Task-35 result |
| --- | --- | --- |
| 31 | `7b670025` | PASS: fresh Task-33 and Task-34 run on canonical main |
| 36 | `emulator-5554` | PASS: fresh Task-33 and Task-34 run on canonical main |

The flows covered v2 actions, restart persistence, A/B isolation, deletion
isolation, document identity, write failure, dual corruption, and no installed
Guest package or Guest ActivityRecord.

Canonical main also passed the Task-36 ten-fixture matrix and Task-37 workspace
recovery smoke on both APIs. Release manifest boundaries remain unchanged.

## Conclusion

Task-35 completed crash-recoverable, serialized state updates with fail-closed
recovery. Separate instance roots remain isolated.
