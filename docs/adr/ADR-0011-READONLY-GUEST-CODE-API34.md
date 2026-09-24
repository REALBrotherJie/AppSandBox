# ADR-0011: Read-only Guest code for API34+ support

## Status

PROPOSED

## Context and proposed decision

Host targetSdk=36. API36 rejects writable imported APK during DexClassLoader
construction with SecurityException. A byte-identical copied APK set read-only
loads and invokes GuestProbe successfully. Production GuestStore currently
publishes writable base.apk; API34+ support is blocked.

Propose finalizing read-only permissions before publishing an imported revision
to the registry or exposing it to code loading. Treat permission failure as an
import failure, align publication with ADR-0003 immutable revisions, and design
safe handling of existing writable revisions. Keep file copying/loading in a
reviewed publication lifecycle; File.setReadOnly alone is not a security boundary.
Implementation and migration are explicitly deferred to the next task.

## Confirmed

Mi 10/API31: writable and read-only both load. Android 16/API36 x86_64 emulator
(4096-byte pages): writable fails at loader construction, read-only succeeds.
Hashes match; Guest remains uninstalled. See ../experiments/V-1-READONLY-DCL-CHECK.md.

## Not confirmed

API34/35 and 16KB-page devices were not tested. Split APKs, writable revision
migration, publication races, filesystem failure handling and production import
fixes are not implemented. This proposal does not modify GuestStore.
