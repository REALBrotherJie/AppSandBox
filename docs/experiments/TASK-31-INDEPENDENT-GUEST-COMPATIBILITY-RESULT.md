# TASK-31 Independent Guest Compatibility Result

日期：2026-09-26

## Fixtures

Two independently built APKs implement contract v1:

- `GuestTestApp`: `com.example.appsandbox.testguest`, layout `exp002_test_layout`, marker `GUEST_A_MARKER_1D1C4B6A`.
- `IndependentGuest`: `com.example.appsandbox.independentguest`, layout `independent_workspace`, marker `INDEPENDENT_GUEST_MARKER_B`.

Their package names, labels, versions, APK contents and SHA-256 values differ. Neither package is installed during Host execution.

## Contract and identity

Host discovers contract version/layout from APK metadata and validates that the declared layout exists. No production code names either fixture package, layout or marker. `GuestInstanceBinding` verifies revisionId, packageName, canonical APK path and SHA before workspace rendering; mismatch in any field fails closed.

## Isolation

Each package renders its own binary layout and marker through Guest resources. A and B counters remain independent across process restart. Deleting A's instance and revision does not change B's marker, counter, artifact or revision. Revision deletion remains blocked while referenced.

## Device results

| API | Serial | Result |
| --- | --- | --- |
| 31 | `7b670025` | PASS: two packages/APKs, distinct markers/SHA, counters 1/2, restart, reference protection, A removal, B retained |
| 36 | `emulator-5554` | PASS: same workflow; both Guest packages absent from PackageManager |

Raw output is ignored under `build/reports/task31/<serial>/`; no tracked device evidence was added.

## Boundaries

The visible runtime remains Host `GuestWorkspaceActivity` plus supported Guest View. No Guest Activity/Application lifecycle, attach, ActivityThread/Binder interception, hidden API or hook is used. Literal fixture markers avoid Android cross-APK numeric resource-ID collisions while layout identity and package resource ownership remain independently verified.

Known limitation: contract v1 validates static layout/resource compatibility and Host-managed state only; it does not imply arbitrary APK or component virtualization support.
