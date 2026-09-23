# Architecture

Phase 1 keeps a small boundary around four responsibilities:

- `MainActivity`: user-facing SAF flow and result presentation.
- `GuestStore`: creates a UUID-backed private guest directory, copies `base.apk`, and appends JSON registry records.
- `GuestPackageReader`: asks the platform `PackageManager` to parse an APK archive and extracts package/component metadata.
- `GuestPackageRecord`: the persisted data contract for this phase.

The runtime is deliberately absent. The imported APK is data only and is never launched.
