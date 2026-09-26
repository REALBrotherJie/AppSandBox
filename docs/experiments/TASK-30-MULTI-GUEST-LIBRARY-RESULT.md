# TASK-30 Multi-Guest Library Result

日期：2026-09-26

## Milestone

AppSandbox now exposes a revision-aware Guest Library. Every successful supported import adds an immutable revision with its own ID, APK path and SHA. Users explicitly select a revision before creating an instance; existing instances retain their original identity after later imports.

## Library and binding

Library rows show label, package, version, revision summary and contract v1 status. Instance rows show package, revision and instance identities. Workspace receives only instanceId, then resolves and verifies the matching GuestStore revision, canonical APK identity and SHA before rendering.

## Reference-safe deletion

`GuestRevisionPolicy` rejects revision deletion while any instance references it. Removing an instance deletes only its data root. After the final reference is removed, GuestStore atomically removes the registry entry and revision artifact; artifact deletion failure restores the registry.

## Risk tests

Tests cover same-package revisions remaining distinct, old-instance identity stability after a new revision, referenced deletion rejection, and successful retry after the last reference is removed. Existing tests continue covering registry corruption, paths, concurrency and rollback.

## Device results

| API | Serial | Assertions | Result |
| --- | --- | --- | --- |
| 31 | `7b670025` | two revisions, bound instances, counters, restart, reference rejection, instance removal, revision retry | PASS |
| 36 | `emulator-5554` | same workflow; Guest package remains uninstalled | PASS |

Raw output is ignored under `build/reports/task30/<serial>/`; no tracked dumps, screenshots or per-device evidence were added.

## Boundaries

The debug-only seam imports a staged APK, opens a resolved instance, and invokes public revision deletion APIs for deterministic automation. It is absent from release. Visible runtime remains Host Activity + supported Guest View; no Guest lifecycle, attach, Binder interception, hidden API or hook is used.

Known limitation: validation used two immutable revisions of GuestTestApp rather than two unrelated third-party packages. The identity model supports different package names, but arbitrary APK compatibility is not claimed.
