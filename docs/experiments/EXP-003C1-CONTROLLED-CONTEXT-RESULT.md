# EXP-003C1 Controlled Context

Status: API31 PASS (debug only); API36 pending. No production runtime is claimed.

Evidence: [write matrix](evidence/task15/7b670025/c1.txt),
[fresh-process read matrix](evidence/task15/7b670025/c1-read.txt).
PID 4993 and 5995 respectively; runCount=1 each. Guest uninstalled before/after.

| Item | C0 | C1 API31 | Classification |
|---|---|---|---|
| Package, loader, resources, assets, ApplicationInfo | Guest | PASS for base/configuration/DP/attribution | BASE_CONTEXT_DOMINANT |
| Application context after binding | Wrapper, cast incorrect | Same Guest Application for all derivatives | C0_DELEGATION_GAP resolved |
| Inflater context and cache | Host | C1, stable identity | C0_DELEGATION_GAP resolved |
| Theme | Host | Guest Resources theme, framework fallback; custom attribute tested in WS-3 | C0_DELEGATION_GAP |
| Files/cache/code-cache/no-backup/data/getDir | Partial facade | All instance paths PASS | BASE_CONTEXT_DOMINANT |
| Preferences/database | Host delegation | Instance paths and persistence PASS | C0_DELEGATION_GAP resolved |
| External files/cache/OBB | Host | Host external root/sandbox/c1 | BASE_CONTEXT_DOMINANT |
| Configuration/DP/attribution derivatives | Host | Guest getters preserved | C0_DELEGATION_GAP resolved |
| Attribution package/UID | Host | Host, tag task15 preserved | HOST_IDENTITY_REALITY |
| ContentResolver/permission/broadcast | Host | Host; harmless package-scoped broadcast received then unregistered | HOST_IDENTITY_REALITY |
| PackageManager/createPackageContext | Guest not installed | NameNotFoundException | UNSUPPORTED |
| Component starts/binds | Not tested | Not called | UNSUPPORTED |
| Display/window contexts | Not implemented | Not called | DEFERRED |

C0 wrote exp003c1.xml into /data/user/0/com.example.appsandbox/shared_prefs.
C1 writes under /data/user/0/com.example.appsandbox/files/task15-instances/c1/
shared_prefs and databases. DP root is /data/user_de/0/com.example.appsandbox/
files/task15-instances/c1. These are logical storage isolation, not OS isolation.
All exact paths are in the matrices. Host preferences/database absence,
Inflater context and unchanged theme checks PASS.

Preferences support all requested scalar types, copied string sets, removal,
clear, listeners, delete and restart persistence. XML uses a custom typed-entry
schema and AtomicFile, not framework XML compatibility. apply is synchronous;
listeners run on the committing thread and are strongly held; clear reports
individual changed keys. No cross-process concurrency or framework cache/file
locking parity is claimed. Delete while holding an older preferences object is
not a supported scenario. External secondary volumes are deferred.

Reproduce: scripts/task15-run.ps1 -Serial 7b670025 -Install -Modes c1,c1-read.
Evidence -> all assertions PASS -> public C1 facade covers this limited matrix.
This does not establish arbitrary application or component compatibility.
