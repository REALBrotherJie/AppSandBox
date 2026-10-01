# M6 RESULT
STATUS: PARTIAL; START `d389724`; FINAL: result commit at branch tip; focused implementation commit plus this RESULT commit; historical untracked task files retained.
CURRENT MAP: PM=`ActivityThread.sPackageManager`; AMS/ATMS=framework singletons; AppOps=`AppOpsManager.mService`; provider=`IContentProvider`+local Binder facade; all production paths now register through `VirtualBinderManager`.
ARCHITECTURE: `BinderCallContext -> IdentityPolicy -> ServiceAdapter -> MethodPolicyRegistry -> physical Binder`; VPM/VAM routes and result adaptation preserved; unknown=diagnosed physical passthrough or explicit block.
IDENTITY: M2 `RuntimeIdentity` reused; logical Guest package/virtual UID remain Guest-facing; system-bound package/UID/AttributionSource become physical Host identity; no kernel/Binder UID spoofing.
ADAPTERS: IPackageManager, IActivityManager, IActivityTaskManager, AppOps, and IContentProvider identity install PASS on API31/API36; provider `asBinder` re-resolution bypass found and fixed generically.
SERVICE MANAGER: `NOT_CURRENTLY_REQUIRED`; pre-Guest singleton/cache replacement covers current boundaries. Legacy system-service hooks removed; client-transaction Activity interceptor remains by design.
UNIT/RISK: registration, policy lookup/result adaptation, unknown pass/block, semantic-position identity, two identities, install idempotency, proxy object methods, and exception preservation PASS.
API36: core install/VPM/system-provider identity/Activity routing PASS; Demo1 CORE=17 PASS, 6 FAIL, 3 BLOCKED, 4 MANUAL after fix; launchMode automation timeouts and incompatible physical/logical assertions remain.
API31: core install/VPM/system-provider identity/Activity routing PASS; Demo1 CORE=18 PASS, 5 FAIL, 3 BLOCKED, 4 MANUAL; MIUI-only unknown methods safely diagnosed/passed through.
DEMO2: Components/System API baseline not completed; Service and Guest Provider remain intentionally outside M6. Demo3 metadata was not re-run.
REGRESSION: M2 first-frame and M3 VPM/classloader/data APIs run; M4 startActivity routing restored; M5 prefs/files/SQLite pass; full M2-M5 aggregate is not PASS.
BUILD: `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleRelease` PASS; evidence in ignored `build/reports/m6/`.
BLOCKERS: Demo1 requires Guest-named Linux process, `ApplicationInfo.uid == Process.myUid`, package-named data roots, and Host-granted self permission, conflicting with authoritative physical/logical identity and M5 roots; no spoof/workaround applied.
BINDER_CORE_FOUNDATION_PROVEN = YES; UNIFIED_IDENTITY_ROUTING_PROVEN = YES; CORE_SERVICE_ADAPTERS_PROVEN = YES
M2_M3_M4_M5_REGRESSION = FAIL; API31 = FAIL; API36 = FAIL; M6_READY_TO_CLOSE = NO
