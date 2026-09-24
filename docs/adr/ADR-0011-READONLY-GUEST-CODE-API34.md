# ADR-0011: Read-only Guest code for API34+ support

## Status

ACCEPTED

## Context and proposed decision

Host targetSdk=36. API36 rejects writable imported APK during DexClassLoader
construction with SecurityException. A byte-identical copied APK set read-only
loads and invokes GuestProbe successfully. Production GuestStore currently
publishes writable base.apk; API34+ support is blocked.

Finalize read-only permissions before publishing an imported revision to the
registry or exposing it to code loading. Treat permission failure as an import
failure, align publication with ADR-0003 immutable revisions, and reject legacy
records without trusted SHA metadata. The implementation marks the staging
file read-only before writing bytes through its already-open descriptor, forces
the descriptor where supported, verifies SHA/size/permissions, then commits the
artifact before atomically updating registry metadata.
Implementation and migration are explicitly deferred to the next task.

## Confirmed

Mi 10/API31 and Android 16/API36 x86_64 emulator (4096-byte pages): production
GuestStore base.apk loads directly and is readable but not writable; reopening
for write returns EACCES. API36 writable negative copy fails at loader
construction; production SHA matches the recorded metadata and Guest remains
uninstalled. See task-16 result evidence.

## Not confirmed

API34/35 and 16KB-page devices were not tested. Split APKs, publisher signing
identity, cross-process locking, and full crash-injection coverage remain open.
SHA-256 is an integrity identifier, not publisher authenticity or a security
sandbox. Legacy writable/no-SHA records return LEGACY_UNVERIFIED and require
re-import; they are not silently chmodded and loaded.
