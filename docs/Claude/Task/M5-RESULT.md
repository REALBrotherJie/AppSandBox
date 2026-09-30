# M5 RESULT
STATUS: PARTIAL
START HEAD: 7e016e9
Architecture: `InstanceStorageManager` is the single owner of canonical instance roots and Java subdirectories; Guest bootstrap consumes it and fails closed on root mismatch/traversal. VPM overlays instance `dataDir` and device-protected path on cloned ApplicationInfo.
Physical layout: `files/virtual/instances/<instance>/{files,cache,code_cache,databases,shared_prefs,no_backup,device,app_custom}`; package APK/metadata remain outside instance data.
ZeroAdapt: public Context probes confirmed files/cache/code_cache/no_backup/getDir/getDataDir/databasePath/ApplicationInfo.dataDir/DP roots, SharedPreferences and SQLite under the selected instance root; restart retained the same instance value.
API31 `7b670025`: PASS for path matrix, instance0/instance1 separation, p0/p1 concurrent processes, restart persistence, Java file/prefs/SQLite creation, path traversal unit/risk coverage.
API36 `emulator-5554`: BLOCKED by device disappearance from adb after prior M4 run; no M5 PASS claim made. Recovery attempted with `adb reconnect`, device remained unavailable.
Lifecycle: existing GuestInstanceStore/Registry delete, atomic registry, canonical-root and symlink rejection tests remain PASS; new storage manager rejects malformed IDs/children. Physical delete/isolation was covered by existing store tests, not device UI.
Regression: build and unit tests pass; M2/M3/M4 source/runtime regression not re-run on API36 due unavailable device. API31 M2/M3/M4 smoke evidence remains from M4 and paths did not alter Activity pipeline.
Risks/limits: Host UID is shared; absolute hardcoded paths, native IO, WebView, external shared storage, Provider/Service, Keystore and Momo native blocker remain deferred. CP path is represented by instance `getDataDir`; full Direct Boot semantics deferred.
New hidden/non-SDK: none beyond existing M2-M4 hooks; PlatformBridge unchanged.
Commit: pending focused commit below. Workspace retains unrelated pre-existing authority/history files.
INSTANCE_JAVA_DATA_ISOLATION_PROVEN = PARTIALLY
INSTANCE_PERSISTENCE_PROVEN = PARTIALLY
INSTANCE_STORAGE_LIFECYCLE_PROVEN = PARTIALLY
M2_M3_M4_REGRESSION = PASS (API31; API36 unverified)
API31 = PASS
API36 = BLOCKED
M5_READY_TO_CLOSE = NO
