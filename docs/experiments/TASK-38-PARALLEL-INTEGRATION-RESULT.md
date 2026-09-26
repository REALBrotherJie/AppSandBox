# TASK-38 Parallel Integration Result

Date: 2026-09-26

## Integrated Head

Integrated commits on top of `99c765a`:

- `70c832e` task-35 crash-safe interactive Guest state
- `9a4e3cd` task-36 contract v2 negative APK matrix
- `571e1e3` task-37 workspace navigation and recovery
- `c6a6a84` task-36 combined-matrix script stabilization

The focused integration fix avoids MIUI API31 framework crashes from stopped
document tasks by having `GuestWorkspaceLauncher.open` start the same validated
`NEW_DOCUMENT` intent and rely on `documentLaunchMode="intoExisting"` for URI
matching. The pre-fix failure was reproduced as an `IAppTask.startActivity`
framework null failure in MIUI, so it was treated as a real navigation gap.

## Build And Unit Tests

- `:app:testDebugUnitTest`: PASS
- `:app:assembleDebug`: PASS
- `:app:assembleRelease`: PASS
- `:test-guests:GuestTestApp:assembleDebug`: PASS
- `:test-guests:IndependentGuest:assembleDebug`: PASS
- `ContractInvalidFixtures` all 10 debug variants: PASS

Confirmed executed JVM suites: `GuestStateStoreTest`, `GuestContractValidationTest`,
`GuestWorkspaceLaunchSpecTest`, `GuestInstanceStoreTest`, and `GuestViewSessionTest`.

Release merged manifest keeps `GuestWorkspaceActivity exported=false` and has no
debug automation or experiment activities.

## Device Matrix

| API | Serial | Task35 | Task36 | Task37 |
| --- | --- | --- | --- | --- |
| 31 | `7b670025` | PASS: fresh run with `status=PASS` | PASS: 10 invalid APKs, no Guest/state residue, v1/v2 usable | PASS: 3 clean full runs |
| 36 | `emulator-5554` | PASS | PASS: 10 invalid APKs, no Guest/state residue, v1/v2 usable | PASS |

API31 Task37 runs:

| Run | A taskId | B taskId | Final B taskId |
| --- | ---: | ---: | ---: |
| `run1` | 596 | 599 | 603 |
| `run2` | 615 | 618 | 622 |
| `run3` | 634 | 637 | 641 |

Each Task37 run checked document URI, instance identity, taskId behavior,
foreground Activity, Recents label, force-stop recovery, close/reopen,
delete/stale fail-closed behavior, final B UI `Counter: 2`, no installed Guest
package, and no Guest ActivityRecord.

## Decision

Task37 is accepted for integration. MIUI may allocate a new taskId when a
stopped document is recreated, but the document URI, instance identity, stored
state, final UI, and fail-closed behavior remain stable. No raw dumps, APKs,
screenshots, or `docs/Codex` files were added.
