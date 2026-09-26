# TASK-34 Concurrent Host Workspace Result

日期：2026-09-26

## Public launch strategy

Each instance uses a stable internal document identity:

```text
component = com.example.appsandbox.GuestWorkspaceActivity
data = appsandbox://workspace/<instanceId>
flags = FLAG_ACTIVITY_NEW_DOCUMENT
```

`GuestWorkspaceActivity` remains `exported=false`, `launchMode=standard`, and uses public `documentLaunchMode=intoExisting`. Different instance URIs create separate Host document tasks. Repeating the same instance URI focuses/reuses its existing task; `FLAG_ACTIVITY_MULTIPLE_TASK` is deliberately absent so one instance does not acquire competing Host copies.

The intent carries only `instanceId`; workspace requires the extra and data URI to agree. Invalid, missing, mismatched, deleted, or corrupt instance state fails closed without selecting the latest or first instance.

## Workspace behavior

Workspace reloads instance, revision, artifact SHA, contract, context, and action session on create, new intent, and later resume. The UI shows package, full instance ID, revision, and contract version. Close removes only the current document task. Delete removes only the current instance and then removes its task.

Pure JVM tests cover component, flags, stable per-instance URI, same-instance reuse identity, different-instance separation, invalid UUIDs, missing identity, and extra/URI mismatch. Existing Store and binding tests cover corrupt registry and artifact/revision/SHA mismatch without fallback.

## Device results

| API | Serial | Result |
| --- | --- | --- |
| 31 | `7b670025` | PASS: two GuestWorkspaceActivity records in distinct taskIds, A/B action isolation, repeat-A reuse, Home/task focus, force-stop restore, close/delete A, stale A fail closed, B retained |
| 36 | `emulator-5554` | PASS: same assertions with distinct taskIds and document URIs |

Both devices confirmed Guest package absence and no Guest ActivityRecord. Raw `dumpsys`, UI XML, and command output remain ignored under `build/reports/task34/<serial>/`; no tracked raw evidence was added.

## Boundary

This is concurrent Host Activity/task multi-open using supported Android document tasks. It does not create Guest Activity records, execute Guest Activity/Application lifecycle, attach Activities, modify framework transactions/Binder, or virtualize arbitrary APK components.

Known limitation: task visibility and recent-task presentation remain platform/launcher policy. The implementation guarantees stable public document identities and verified Host records, not system split-screen placement or Guest Activity virtualization.
