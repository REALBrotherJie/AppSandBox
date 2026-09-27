# Task-44 Logical Guest Dispatch Result

Date: 2026-09-27

## Scope

Added a logical dispatch layer under `dispatch/`. It consumes the approved
revision-bound explicit resolver result and also accepts a resolved component
from the bounded implicit resolver boundary without implementing another
parser. No Android component lifecycle is started.

The layer creates immutable `ActivityPlan`, `ServicePlan`, `ReceiverPlan`, and
`ProviderPlan` values. Every request is bound to operation ID, instance,
revision, package, class, type, caller scope, and operation. Instance-specific
task/service/receiver/provider namespaces include both instance and revision.

## Failure And Recovery Model

The engine revalidates instance binding, revision/package identity, artifact
integrity, component state, exported/permission policy, operation/type pairing,
argument limits, and provider `content://` URI shape before commit.

Stable failures include deleted instance, revision/package/artifact mismatch,
component rejection, unsupported operation, runtime unavailable, duplicate
operation, stale plan, malformed input, and corrupted dispatch state.
Duplicate operation IDs are idempotent only for the same request.

`prepare` is not persisted. Only committed plans are written to the optional
atomic dispatch journal; a new engine recovers committed plans, while a
prepared-only plan is absent. Teardown removes one instance namespace and
leaves other instances and revision artifacts untouched.

The runtime session is an availability gate. Exceptions and unavailable
runtime sessions fail closed without invoking Android lifecycle APIs or
tearing down Host/workspace state. The default gate is unavailable until the
approved runtime session implementation is supplied.

## Verification

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | PASS, 86 tests, 1 pre-existing skipped |
| Host Debug/Release | PASS |
| GuestTestApp / IndependentGuest | PASS |
| ContractInvalidFixtures | PASS |
| ResolverFixtures | PASS |
| API31 device | NOT EXECUTED |
| API36 `emulator-5556` | NOT EXECUTED |

No device command was run. API31 and `emulator-5554` were not touched.
API36 `emulator-5556` requires explicit availability confirmation before use.

Real Activity/Service/Receiver/Provider lifecycle, system task identity,
background service execution, broadcast delivery, and provider IPC remain
unsupported by design.
