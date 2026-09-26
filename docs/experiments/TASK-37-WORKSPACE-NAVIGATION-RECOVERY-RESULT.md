# TASK-37 Workspace Navigation and Recovery Result

日期：2026-09-26

## Scope

Task-37 completes product navigation and recovery on top of the Task-34 document-task model:

- Library and workspace lists expose `Open` for a new document task and `Focus` when the instance task already exists.
- A repeated launch resolves the same document URI and focuses the existing instance task.
- Different instance identities retain different document task IDs.
- Workspace recents labels include the guest package name and an instance prefix.
- `onCreate`, `onNewIntent`, `onResume`, and process recreation re-parse and validate the workspace identity and persisted binding.
- Closing A removes only A's task and allows A to be opened again.
- Deleting A removes its task; a stale A intent fails closed while B remains usable.

## Verification

The focused device workflow is `scripts/task37-run.ps1`. It writes transient command/UI/task dumps below `build/reports/task37/<serial>/<run>/`; these are not tracked as evidence.

| API | Serial | Result |
| --- | --- | --- |
| 31 | `7b670025` | PASS: three clean, complete runs after stopped-task recovery fix |
| 36 | `emulator-5554` | PASS: complete combined-code run |

Covered flows:

- launcher start after Home
- recents presentation with package/instance labels
- repeated-instance task focus
- distinct instance task IDs and isolated state
- force-stop and relaunch recovery
- close and reopen
- delete and stale-task fail-closed behavior
- Guest package remains uninstalled and no Guest ActivityRecord is created

## API31 Recovery

Before the fix, a clean run reached Recents, then `AppTask.startActivity` after
force-stop caused a MIUI framework `DisplayContent.layoutAndAssignWindowLayersIfNeeded()`
null failure and returned to Launcher. This was a real navigation failure, not
just UI dump churn. `GuestWorkspaceLauncher.open` now starts the same validated
document intent for both new and existing tasks; `intoExisting` matches its URI.

Three post-fix runs each began with `pm clear` and passed the entire workflow:

| Run | A taskId | B taskId | Final B taskId | Result |
| --- | ---: | ---: | ---: | --- |
| `run1` | 596 | 599 | 603 | PASS |
| `run2` | 615 | 618 | 622 | PASS |
| `run3` | 634 | 637 | 641 | PASS |

Each run checked the document URI against its instance identity, reused A's
taskId on repeat-open, found distinct A/B tasks, confirmed the workspace
Activity and Recents labels, restored counters A=1/B=2 after force-stop,
and ended on B's document with B=2 in the foreground UI. Guest installation
and Guest ActivityRecord checks were negative. MIUI can assign a new taskId
when a stopped document is recreated; the document URI and persisted instance
identity remain stable.

## Local checks

- `:app:testDebugUnitTest`: PASS
- `:app:assembleDebug` and `:app:assembleRelease`: PASS
- `git diff --check`: PASS

The combined build, Guest fixtures, and Task-35/36 device matrix are recorded
in `TASK-38-PARALLEL-INTEGRATION-RESULT.md`. No `docs/Codex` file was modified.
