# EXP-003 Host leakage matrix

Task-15 supersedes speculative attribution in the earlier matrix. Tested:
Mi 10/API31 and Android 16/API36 x86_64 emulator. C0 remains unchanged.
Evidence: EXP-003C1-CONTROLLED-CONTEXT-RESULT.md, EXP-003C-MIN-ONCREATE-RESULT.md,
EXP-003B1-SAME-PACKAGE-MULTI-INSTANCE-RESULT.md and their raw evidence links.

| Surface | C0 -> C1 observation on both APIs | Classification / boundary |
|---|---|---|
| package name | Guest -> Guest | BASE_CONTEXT_DOMINANT; system caller stays Host |
| op package name / getOpPackageName | Host -> Host | HOST_IDENTITY_REALITY |
| UID/PID | Host -> Host | HOST_IDENTITY_REALITY, no fabricated UID |
| class loader | Guest -> Guest | BASE_CONTEXT_DOMINANT |
| resources/assets | Guest -> Guest | BASE_CONTEXT_DOMINANT; Assets shared between tested instances |
| ApplicationInfo / getApplicationInfo | Guest logical copy -> Guest copy per derived context | BASE_CONTEXT_DOMINANT; not PMS identity |
| data/files/cache/getDataDir/file APIs | Guest -> Guest, extended C1 storage matrix | BASE_CONTEXT_DOMINANT |
| noBackup/codeCache/getDir | Guest paths -> Guest paths for derivatives too | BASE_CONTEXT_DOMINANT |
| PackageManager | Guest query fails -> still fails | UNSUPPORTED; NameNotFoundException |
| ContentResolver | Host -> Host | HOST_IDENTITY_REALITY; provider operations DEFERRED |
| system services | Host -> cached C1 inflater, others Host | C0_DELEGATION_GAP repaired for inflater only |
| process name | Host -> Host | HOST_IDENTITY_REALITY |
| attribution source | Host -> Host with preserved task15 tag | HOST_IDENTITY_REALITY |
| SharedPreferences | Host storage leak -> instance XML, restart read PASS | C0_DELEGATION_GAP repaired for tested subset |
| database | Host -> instance SQLiteOpenHelper, restart read PASS | C0_DELEGATION_GAP repaired |
| external files/cache/OBB | Host -> Host root/sandbox/instance | BASE_CONTEXT_DOMINANT, logical isolation only |
| Activity/service launch/bind | Not called -> not called | UNSUPPORTED; no component implementation |
| Application.mLoadedApk | Not observed -> not observed | DEFERRED, not approved |
| Application.attach / ContextImpl.getImpl | Only public newApplication outcomes observed | Internal state DEFERRED; no field reads |
| getPackageCodePath / resource path | Guest APK -> Guest APK | BASE_CONTEXT_DOMINANT |
| getApplicationContext | Wrapper -> Guest Application after bind | C0_DELEGATION_GAP repaired |
| derived configuration Context | Host -> C1 Guest resources/configuration | C0_DELEGATION_GAP repaired |
| derived device-protected Context | Host -> C1 instance under Host device-protected root | C0_DELEGATION_GAP repaired |
| derived attribution Context | Host -> C1 with Host attribution | C0_DELEGATION_GAP repaired; identity remains Host |
| derived display/window Context | Not tested -> not implemented | DEFERRED |
| createPackageContext | NameNotFoundException -> same | UNSUPPORTED |
| ContextImpl.mPackageInfo | Not observed -> not observed | DEFERRED |
| LoadedApk.getApplication | Not observed -> not observed | DEFERRED, public binding is not evidence of hidden cache |
| LoadedApk.getClassLoader | Not observed -> not observed | DEFERRED |
| LoadedApk.getResources | Not observed -> not observed | DEFERRED |
| LoadedApk.getApplicationInfo | Not observed -> not observed | DEFERRED |
| ActivityThread package caches | Not observed -> not observed | DEFERRED; M-A/M-B only test public outcomes |
| application context lookup | Wrapper, invalid Guest Application cast -> cast PASS | C0_DELEGATION_GAP repaired |
| Application.getApplicationContext in B0-P | Controlled wrapper -> C1-bound Guest Application | Corrected semantic contract |
| AppComponentFactory | Guest factory bypassed -> unchanged public Instrumentation limit | UNSUPPORTED for Guest custom factory |
| LayoutInflater from Application | Host-sensitive -> clone with C1 context | C0_DELEGATION_GAP, not LoadedApk evidence |
| registerReceiver/sendBroadcast | Host delegation -> harmless package-scoped test received | HOST_IDENTITY_REALITY, no Guest manifest component |
| checkSelfPermission | Host -> Host authorization | HOST_IDENTITY_REALITY |
| Application callbacks | Registration only -> registration only | DEFERRED dispatch; no Guest Activity lifecycle claim |
| Multi-instance statics | B0 not tested -> shared in M-A, separate in M-B | LIMITED Java isolation, no process-model decision |

Hidden observation request = NOT NEEDED NOW. No measured C1 gap currently
requires a non-public field read. Factory/lifecycle/component compatibility,
malicious Guest isolation and general service virtualization are not solved.
