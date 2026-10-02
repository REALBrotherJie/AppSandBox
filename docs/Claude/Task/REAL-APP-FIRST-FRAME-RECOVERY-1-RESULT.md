# REAL APP FIRST FRAME RECOVERY 1 RESULT
START_HEAD = b805b2ae86eb95e6a5ef14d73024c086cefb7b1b; DEVICES = API31 7b670025 + API36 emulator-5554; two instances per app/API
FIXES = idempotent absolute database path mapping; instance/package/Virtual-UID-scoped lazy self-provider routing through existing VirtualProviderManager
MOMO = API31/API36 I0+I1 stable interactive first frame; WorkManager DB opens without double-prefix or SQLITE_CANTOPEN
WPS = non-exported self-provider denial removed on both APIs; API31 next blocker RePlugin BinderCursor null; API36 next blocker arm64 libcp-lib.so on x86_64
SEVEN_APP_MATRIX = Momo PASS; Chrome JNI; Termux PendingIntent/Receiver; Coolapk provider stall; Zhihu native/ABI; WPS RePlugin/ABI; QQ Browser permission/visibility
FIRST_BLOCKER_CHANGED_APPS = 2 (Momo, WPS); EVIDENCE = build/reports/real-app-recovery-1/
REGRESSION = unit PASS; M8 Provider CRUD/client PASS both; M10 2/2 PASS both; M9 25 PASS + 1 BLOCKED + 2 MANUAL both; API31 M8 receiver harness manifest+ordered timeout, API36 manifest timeout
STATUS = PARTIAL
MOMO_PATH_DOUBLE_REWRITE_FIXED = YES; PATH_REWRITE_IDEMPOTENCE_PROVEN = YES
WPS_PROVIDER_IDENTITY_FIXED = YES; GUEST_SELF_PROVIDER_ACCESS_PROVEN = YES; CROSS_INSTANCE_PROVIDER_ISOLATION_PROVEN = YES
API31 = PASS; API36 = PASS
BASELINE_FIRST_FRAME = 0/7; AFTER_FIX_FIRST_FRAME = 1/7
MOMO_FIRST_FRAME = PASS; WPS_FIRST_FRAME = FAIL
MOMO_NEXT_BLOCKER = Host physical INTERNET capability on API31 (non-fatal first frame); no startup blocker on API36
WPS_NEXT_BLOCKER = API31 RePlugin BinderCursor null; API36 arm64 libcp-lib.so ABI mismatch
CHROME_FIRST_BLOCKER = J.N.ZO JNI (API31); null Context then JNI (API36)
TERMUX_FIRST_BLOCKER = PendingIntent mutability (API31); dynamic Receiver export flag (API36)
TOP_FIRST_BLOCKER_AFTER_FIX = NATIVE_JNI_ABI_STARTUP
M12_REMAINS_FROZEN = YES; M13_STARTED = NO; PRODUCTION_RUNTIME_MODIFIED = YES; STOPPED_AFTER_RECOVERY_ROUND_1 = YES
