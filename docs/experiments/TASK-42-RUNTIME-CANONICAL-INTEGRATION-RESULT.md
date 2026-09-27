# TASK-42 Runtime Boundary And Canonical Integration Result

Date: 2026-09-27

## Scope

Task-40 resolver commit `204a7e1` was semantically integrated onto canonical
`main` without reverting Task-39 coverage. Task-41 runtime/IPC changes were
reviewed and integrated as the contract-v2 action runtime path.

The runtime boundary is same-UID, separate-process only:
`GuestRuntimeService` is non-exported and runs in
`com.example.appsandbox:guest_runtime`. It does not claim an isolated UID or
app-sandbox boundary.

## Offline Verification

- `:app:testDebugUnitTest`: PASS
- `:app:assembleDebug`: PASS
- `:app:assembleRelease`: PASS
- `:test-guests:GuestTestApp:assembleDebug`: PASS
- `:test-guests:IndependentGuest:assembleDebug`: PASS
- `:test-guests:ContractInvalidFixtures:assembleDebug`: PASS
- `:test-guests:ResolverFixtures:assembleDebug`: PASS
- Release manifest: no debug automation; runtime service is
  `exported=false`, `process=":guest_runtime"`.

Runtime tests cover opaque tokens, stale/deleted instances, revision/artifact
mismatch, unsupported actions, state corruption, concurrent state updates,
bounded Binder death reconnect, bind timeout, malformed errors, close, and old
token rejection.

## Device Matrix

| API | Serial | Result |
| --- | --- | --- |
| 31 | `7b670025` | PASS |
| 36 | `emulator-5554` | NOT EXECUTED: simulator was not stable online and was not claimed |

API31 result summary from `build/reports/task42/7b670025/result.txt`:

- Host PID `23198`, runtime PID before kill `24246`, runtime PID after kill
  `30176`.
- Host UID `10293`, runtime UID `10293`: same UID, separate process.
- A/B state isolation: PASS.
- Kill `:guest_runtime`, keep Host/workspace alive, reconnect and continue:
  PASS.
- Delete A after opening runtime session, then old token rejected as
  `deleted_instance`: PASS.
- B remains usable after deleting A and after Host force-stop: PASS.
- Guest package installed on device: false.
- Guest ActivityRecord check: PASS.

## Decision

Task-40 and Task-41 are accepted for canonical integration. API36 remains a
fresh validation gap for the next available stable emulator run; no old API36
result was reused.
