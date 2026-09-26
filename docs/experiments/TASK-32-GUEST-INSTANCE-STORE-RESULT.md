# TASK-32 GuestInstanceStore Stateful I/O Result

日期：2026-09-26

## Scope

`GuestInstanceStoreTest` directly exercises `create`, `list`, `get`, and `delete` against temporary filesystem roots and a real immutable test artifact. Production keeps the `Context` constructor; an internal root/file-ops constructor is the only testability seam.

## Store API coverage

- Normal create/list/get/delete and process-style Store reopen: PASS.
- Create registry-write failure preserves the old registry, removes the failed instance root, and leaves no `.tmp`: PASS.
- Corrupt primary and backup fail with `CORRUPT` from create/list/get: PASS.
- Relative/absolute invalid IDs and persisted external `dataRoot` are rejected without external deletion or registry replacement: PASS.
- Sequential duplicate UUID preserves the existing record and directory: PASS.
- Concurrent different UUIDs through separate Store objects preserve all unique records and roots: PASS.
- Concurrent same UUID permits one winner and preserves its directory: PASS.
- Delete registry-write failure preserves both instances, roots, and Guest artifact: PASS.
- Delete-tree failure restores the registry; retry deletes only A while B and the Guest artifact remain: PASS.
- Symbolic-link escape: SKIPPED because this Windows/JVM session could not create a directory symlink; JUnit reports the skip explicitly instead of treating it as PASS.

The same-UUID test exposed and fixed a cleanup race: a losing creator no longer deletes the winning creator's directory. Registry update now maps unreadable primary/backup state to explicit `CORRUPT` for Store create as well as reads.

## Reliability boundary

Temporary-directory rename, backup, deletion, rollback, and multi-Store locking are reliably exercised on the current JVM filesystem. Android permission enforcement, power-loss durability after `fsync`, and symlink rejection on a symlink-capable device are not reliably simulated here. Those remain candidates for targeted instrumentation/device verification if the storage backend changes.

## Verification

- `:app:testDebugUnitTest`: PASS; Store suite 10 tests, 9 passed and 1 symlink capability skip.
- `:app:assembleDebug`: PASS.
- `:app:assembleRelease`: PASS.
- `:test-guests:GuestTestApp:assembleDebug`: PASS.
- No tracked raw logs, dumps, screenshots, or per-device evidence were added.

Production plus test additions exceed this result document's added lines. Known limitation: JVM fault injection proves Store rollback contracts but does not emulate sudden power loss or Android kernel permission semantics.
