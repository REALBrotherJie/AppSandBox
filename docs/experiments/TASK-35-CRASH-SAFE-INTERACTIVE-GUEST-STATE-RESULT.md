# TASK-35 Crash-Safe Interactive Guest State Result

Date: 2026-09-26

## Result

Contract v2 counter state uses an instance-local `GuestStateStore` behind
`GuestViewSession`, preserving the `counter()` and `execute()` API. Writes are
serialized per instance with a JVM lock and lock file, forced to a temporary
file, then atomically replaced. A valid backup repairs a missing or corrupt
primary; dual corruption fails closed. Schema, range, SHA-256 checksum, and
symbolic-link validation reject invalid state and paths. Persistence failures
reach the binder failure path instead of silently resetting the counter.

State files live under `<instanceRoot>/files/counter.txt` with `.bak`, `.tmp`,
and `.lock` companions.

## Verification

JVM tests cover concurrent updates from independent sessions, reset/toggle
contention, atomic cleanup, stale temporary files, backup recovery, dual
corruption, symbolic-link rejection, and separate instance roots.

`scripts/task35-run.ps1` repeats Task-33 and Task-34 device flows; raw output
stays in ignored `build/reports/task35/<serial>/`.

| API | Serial | Original Task-35 result |
| --- | --- | --- |
| 31 | `7b670025` | PASS: Task-33 and Task-34 regression |
| 36 | `emulator-5554` | PASS: Task-33 and Task-34 regression |

The device regression covered v2 increment/reset/toggle, restart persistence,
two-instance and deletion isolation, concurrent document identity, repeat-open
reuse, and no installed Guest package or Guest ActivityRecord.

Original Task-35 build checks passed: `:app:testDebugUnitTest`,
`:app:assembleDebug`, `:app:assembleRelease`, both valid Guest debug APKs, and
`git diff --check`. Release and Host activity boundaries were unchanged.

## Conclusion

Task-35 completed crash-recoverable, serialized state updates with fail-closed
recovery. Separate instance roots remain isolated. Task-38 records the later
combined-code regression result separately.
