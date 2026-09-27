# TASK-40 Explicit Component Resolver Result

Date: 2026-09-26

## Scope

Guest revisions now persist a normalized, revision-bound manifest for Activity,
Service, Receiver, and Provider components. Explicit requests carry package,
revision, class name, component type, and caller scope. Relative names and
fully-qualified names normalize to the package; package escapes, path/control
characters, invalid permissions, and overlong names fail closed.

The resolver does not fall back to the newest revision. It returns stable
rejection reasons:

| Condition | Reason |
| --- | --- |
| malformed request | `invalid-request` |
| missing revision | `revision-not-found` |
| package does not match revision | `package-mismatch` |
| class absent from requested revision | `not-found` |
| class exists with another type | `type-mismatch` |
| component disabled | `disabled` |
| host resolves non-exported component | `not-exported` |
| component declares a permission | `permission-required` |
| stored component is not bound to its revision/package | `revision-mismatch` |

`GUEST_INTERNAL` may resolve non-exported components. `HOST_EXTERNAL` requires
exported components. Permission-bearing components are rejected until an
explicit permission-capability model exists.

## Persistence And Parsing

Guest registry schema 3 stores the component manifest with each revision.
Registry reads reject old/unknown schemas, missing manifests, duplicate
components, non-normalized names or permissions, summary mismatches, and
components whose revision/package binding is inconsistent. Archive parsing
requests disabled components so disabled declarations are retained in the
normalized manifest. Library rows expose per-type component counts.

## Verification

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | PASS |
| Host debug and release APKs | PASS |
| `GuestTestApp` and `IndependentGuest` debug APKs | PASS |
| Resolver fixture v1/v2 debug APKs | PASS |
| All 10 ContractInvalidFixtures debug variants | PASS |
| API 31 `7b670025` | PASS |
| API 36 `emulator-5554` | PASS |

Both device runs resolved Activity, Service, Receiver, and Provider cases;
checked disabled, non-exported, permission, wrong-type, and revision-binding
rejections; confirmed no resolver fixture or Guest package was installed; and
confirmed the existing v1/v2 workspaces remained usable without instance/state
mutation.

Implicit intent-filter resolution and launch semantics are intentionally
deferred. This task establishes the explicit, revision-bound lookup boundary
only.
