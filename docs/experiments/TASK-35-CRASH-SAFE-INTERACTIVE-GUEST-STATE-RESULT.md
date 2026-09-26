# TASK-35 Crash-Safe Interactive Guest State Result

日期：2026-09-26

## Scope

Contract v2 counter state now uses an instance-local `GuestStateStore` behind
`GuestViewSession`. The store keeps the existing `counter()` and `execute()`
API while adding:

- strict schema, range, and SHA-256 checksum validation;
- per-instance JVM serialization plus a lock file for concurrent Host
  sessions/processes;
- forced temporary writes followed by atomic state-file replacement;
- backup rotation before replacing an existing valid primary;
- recovery from a valid backup when the primary is missing or corrupt;
- fail-closed behavior when no valid state remains;
- rejection of symbolic-link roots, directories, state files, lock files,
  backup files, and temporary files.

The state files are scoped below the supplied instance root:

```text
<instanceRoot>/files/counter.txt
<instanceRoot>/files/counter.txt.bak
<instanceRoot>/files/counter.txt.tmp
<instanceRoot>/files/counter.txt.lock
```

`GuestViewSession` does not catch persistence failures or substitute zero, so
the existing binder failure path can disable actions and report the failure.

## Tests

JVM coverage includes:

- concurrent increments from independent sessions with no lost updates;
- reset/toggle contention without partial files;
- backup recovery and primary repair;
- dual corruption fail closed;
- stale temporary-file handling;
- atomic commit cleanup;
- instance-root/state-file symbolic-link rejection;
- concurrent independence of separate instance roots.

## Device Regression

`scripts/task35-run.ps1` runs equivalent Task-33 and Task-34 workflows with
bounded UI polling, without changing their production or fixture files. Raw
UI/ADB output remains under ignored `build/reports/task35/<serial>/`.

| API | Serial | Result |
| --- | --- | --- |
| 31 | `7b670025` | PASS: Task-33 and Task-34 regression |
| 36 | `emulator-5554` | PASS: Task-33 and Task-34 regression |

The regression confirms v2 increment/reset/toggle, restart persistence,
two-instance isolation, deletion isolation, concurrent Host workspace
identity, repeat-open reuse, and absence of installed Guest packages or
Guest Activity records.

## Build

Passed:

```text
:app:testDebugUnitTest
:app:assembleDebug
:app:assembleRelease
:test-guests:GuestTestApp:assembleDebug
:test-guests:IndependentGuest:assembleDebug
git diff --check
```

Release and Host activity boundaries were unchanged. No Guest fixture,
Manifest, `GuestWorkspaceActivity`, `MainActivity`, `GuestPackageReader`,
workspace launcher, or `docs/Codex` file was modified.

## Modified Files

```text
app/src/main/java/com/example/appsandbox/contract/GuestViewSession.kt
app/src/main/java/com/example/appsandbox/contract/state/GuestStateStore.kt
app/src/test/java/com/example/appsandbox/contract/state/GuestStateStoreTest.kt
scripts/task35-run.ps1
docs/experiments/TASK-35-CRASH-SAFE-INTERACTIVE-GUEST-STATE-RESULT.md
```

## Conclusion

Task-35 is complete. Contract v2 state updates are serialized per instance,
crash-recoverable through a durable temporary write and backup, and fail
closed when recovery cannot produce a validated state. Separate instance
roots remain isolated.
