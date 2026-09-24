# Task-16 GuestStore hardening result

## Status

CONFIRMED for the tested single-process APK revision path on API31 and API36.
Activity remains DESIGN NEXT and was not implemented.

## Prior state

GuestStore wrote `files/guests/<id>/base.apk` as writable bytes, overwrote the
registry with a JSON array, persisted no SHA/size/schema, and `latestRecord()`
silently returned null for damaged/missing state. API36 therefore rejected the
production code source.

## Final transaction

SAF URI -> temporary validation copy in MainActivity -> GuestStore staging UUID
-> open `staging/<tx>/base.apk` -> set read-only before writing -> stream bytes
and SHA-256 -> flush/force/close -> PackageManager archive parse -> create new
guestId/revisionId record -> rename artifact into `guests/<guestId>/base.apk`
-> verify file, size, SHA and non-writable state -> AtomicFile schema-v2 registry
write -> expose record. Any failure removes staging; source and temp streams close.

## Production verification

| Device | guestId | size / SHA | read-only | production DCL |
|---|---|---|---|---|
| Mi 10/API31 | `89951ab4-ac8c-4f7d-b420-9ff4af52f3b7` | 30356 / `666fbb1ec28d5c36e8dcde928e67bbd95fc31d9025beeaff1af3aace2b536494` | canRead=true, canWrite=false, reopen EACCES | GuestProbe PASS |
| API36 emulator | `e24bb01a-e348-4842-aec5-3db5dfe164df` | 30356 / same | canRead=true, canWrite=false, reopen EACCES | GuestProbe PASS |

Both reports were fresh processes (`runCount=1`), Guest `pm path` was empty
before/after, and no task15 read-only copy was used. Revision IDs are persisted
in schema-v2 registry records; each import creates a new guest/revision directory.

## Integrity and registry

`GuestArtifactVerifier` returns VALID, MISSING_ARTIFACT, HASH_MISMATCH or
LEGACY_UNVERIFIED. Malformed registry raises CORRUPT instead of becoming an
empty store. Old JSON-array records remain readable only as legacy records and
are rejected by the integrity guard until re-imported. AtomicFile protects
registry replacement; the process-local monitor serializes mutations. Artifact
commit precedes registry publication.

## Tests

Six JVM unit tests pass: known SHA, valid immutable artifact, missing artifact,
hash mismatch, legacy missing metadata and size mismatch. Device verification
also covered direct production DCL on both APIs and API36 writable negative:
`SecurityException: Writable dex file ... is not allowed.` The final build
commands were `:app:testDebugUnitTest`, `:app:assembleDebug`,
`:app:assembleRelease`, and `:test-guests:GuestTestApp:assembleDebug`.

## Limitations

No full crash-injection test was added for process death between artifact rename
and registry commit; orphan cleanup is intentionally conservative. No
cross-process lock, split APK metadata, signing publisher identity or 16KB page
test is claimed. SHA proves current bytes equal recorded bytes; it is not a
malware verdict, publisher signature, or OS sandbox.
