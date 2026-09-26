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

The focused device workflow is `scripts/task37-run.ps1`. It writes transient command/UI/task dumps below `build/reports/task37/<serial>/`; these are not tracked as evidence.

| API | Serial | Result |
| --- | --- | --- |
| 31 | `7b670025` | PARTIAL: workspace launch, Focus behavior, task labels, and manual recovery checks observed; full scripted run was blocked by intermittent MIUI task/uiautomator state churn while switching document tasks |
| 36 | `emulator-5554` | PASS |

Covered flows:

- launcher start after Home
- recents presentation with package/instance labels
- repeated-instance task focus
- distinct instance task IDs and isolated state
- force-stop and relaunch recovery
- close and reopen
- delete and stale-task fail-closed behavior
- Guest package remains uninstalled and no Guest ActivityRecord is created

## Local checks

- `:app:testDebugUnitTest`: PASS
- `:app:assembleDebug`: PASS
- `git diff --check`: PASS

The API31 device showed the expected workspace task label in `dumpsys` (`com.example.appsandbox.testguest / <instance-prefix>`) and exposed both document tasks in recents during successful portions of the run. Its MIUI launcher intermittently removed the focused document from the activity dump during automation, then restored the workspace on a subsequent UI dump; the result is recorded as partial rather than PASS.

The required session-requirements file `docs/Codex/00_SESSION_REQUIREMENTS.md` was not present in the provided worktree. No files under `docs/Codex` were modified.
