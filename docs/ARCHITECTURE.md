# Architecture

> **Status: Superseded as the Phase 1 snapshot.** The current design baseline is [APP_SANDBOX_MASTER_DESIGN.md](APP_SANDBOX_MASTER_DESIGN.md) and the documents under `docs/design/`.

Phase 1 keeps a small boundary around four responsibilities:

- `MainActivity`: user-facing SAF flow and result presentation.
- `GuestStore`: creates a UUID-backed private guest directory, copies `base.apk`, and appends JSON registry records.
- `GuestPackageReader`: asks the platform `PackageManager` to parse an APK archive and extracts package/component metadata.
- `GuestPackageRecord`: the persisted data contract for this phase.

The runtime is deliberately absent. The imported APK is data only and is never launched.

This Phase 1 snapshot remains as historical context; it is not the complete framework architecture.
