# ADR-0012: GuestStore atomic registry and integrity

## Status

ACCEPTED

## Decision

Keep the small JSON GuestStore, but publish only immutable revision artifacts.
Each import uses a UUID staging directory, computes SHA-256 while copying,
marks the opened destination read-only before content writes, flushes/forces
the descriptor where supported, validates the APK, then renames the completed
artifact into its immutable guest directory. Registry metadata is written as
schema version 2 through Android `AtomicFile`. Mutations use a process-local
monitor; this is not a cross-process lock.

Registry publication happens after artifact commit. A failed registry write can
leave an orphan artifact for conservative future recovery, but it cannot create
a registry pointer to a missing artifact. Staging is deleted on failure.

Records persist revisionId, sha256, fileSize, package identity, version data,
component summary and absolute artifact path under the existing project layout.
Missing files, size/hash mismatch, corrupt registry, and legacy records without
trusted integrity metadata are explicit failures. Legacy records are not
silently upgraded by hashing their current bytes; users must re-import.

## Evidence and limits

API31 Mi 10 and API36 emulator production-path EXP-001 succeeded directly from
GuestStore base.apk. Both devices reported canWrite=false and EACCES on reopen.
Unit tests cover SHA and artifact state validation; device scripts cover import,
metadata, read-only behavior, direct DCL and API36 writable rejection.
AtomicFile crash injection, cross-process mutation, split APKs, publisher
signing identity and 16KB pages remain future work. SHA does not provide a
security sandbox or publisher authenticity.
