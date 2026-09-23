# Guest Package Runtime

## Registry role

The Guest Package Registry is the authoritative AppSandbox-owned description of an imported APK. It is not a replacement PMS visible to Android. It supports validation, resolution, runtime construction, migration, and diagnostics.

Required domain records:

- package identity, revision, APK digest, version name/code, signing digest
- application label/icon/resource metadata
- activities, services, receivers, providers
- process declarations and exported/enabled state
- permissions and requested features
- intent filters, provider authorities, grants
- native library ABIs and archive entries
- source URI/provenance and import timestamps

## Model choice

`PROPOSED`: use immutable AppSandbox domain models, with explicit serialization DTOs. Do not persist platform `PackageInfo` objects directly.

Advantages: stable schema, testability, no hidden/transient fields, controlled migration, no accidental platform object sharing. Disadvantages: mapping work and versioned schema maintenance.

Platform objects are useful at the import boundary and disposable after normalization. They contain SDK-version differences and fields whose semantics assume installation.

## Revision model

```text
GuestPackageKey = packageName + signing identity policy
GuestPackageRevision = immutable APK metadata + digest
GuestInstance = revision reference + instance-specific state
```

Same packageName with a different signing identity must not silently replace an existing revision. Exact policy is `RESEARCH NEEDED` for multi-signer/rotation cases.

## Intent resolution

The resolver must accept an explicit or implicit intent and a caller logical identity. It evaluates:

1. explicit component/package restrictions;
2. action;
3. categories;
4. URI scheme/authority/path;
5. MIME type;
6. enabled/exported state;
7. required permission and guest policy;
8. user/instance scope;
9. deterministic priority and tie-breaking.

System-installed targets and guest targets are separate namespaces at first. Crossing the boundary requires an explicit policy decision and a host-mediated intent.

`PROPOSED`: resolver output is an immutable `GuestResolution` containing target component, reason, required capabilities, and a stable diagnostic trace. Do not reuse `ResolveInfo` as the persistent model.

## Open issues

- Exact manifest normalization across API 28-36.
- Package visibility behavior for host-facing queries.
- Split APK/app bundle import policy.
- Signing certificate rotation and rollback.
- Shared user IDs and shared libraries: likely unsupported initially.
