# M9 Limit Correction Result
STATUS = PASS; START_BRANCH = main; START_HEAD = 860f5511d91adf50bd539e2b71ae6af4c6340ebc; FINAL_HEAD = result commit
COMMITS = 39505b4 (Guest Context storage routing), result commit (this file)
Guest Context root cause: `ContextWrapper` delegated unoverridden `getCodeCacheDir/getDir` to Host; both now use the canonical VirtualInstance CP root and reject path escape.
Old/new: Host `/data/user/0/com.example.appsandbox/{code_cache,app_*}` -> `<instanceRoot>/{code_cache,app_*}`.
API31/API36 D1 matrix: data/files/cache/codeCache/noBackup/getDir/database/device-protected all PASS; `D1.DATA.APP_DIRS=PASS`.
Two-instance Java evidence: `java-a/java-b` have distinct roots and distinct code-cache/getDir values on both devices; JAVA_CODE_CACHE_INSTANCE_ISOLATION=PASS; JAVA_GETDIR_INSTANCE_ISOLATION=PASS.
M5_CURRENT_HEAD=PASS; M7_STORAGE_SMOKE=PASS; M8_PROVIDER_STORAGE_SMOKE=PASS (CRUD/client and instance-root provider context).
M9_NATIVE_SMOKE_AFTER_CONTEXT_FIX=PASS; constructor/JNI_OnLoad/hardcoded/stat/realpath/context IO remain PASS; direct syscall remains expected boundary.
Dual-slot: API31/API36 `p1->concurrent-a`, `p2->concurrent-b` simultaneously alive; identical logical path produced distinct physical-root timestamp values; CONCURRENT_NATIVE_INSTANCE_ISOLATION=PASS.
Same-slot rebind: API31 PID 30486->dead->7306; API36 PID 3442->dead->3614; deletion removed registry/storage, then p1 bound `rebind-b` root and hardcoded IO PASS.
PROCESS_SLOT_REBIND_PROVEN=YES; STALE_NATIVE_BINDING_PROVEN_ABSENT=YES; hook lifetime=process lifetime and fresh process binding has no old native state.
M2/M3/M5/M7/M8 runtime PASS on both; M4_RUNTIME=PASS and M4_AUTOMATION_HARNESS=BLOCKED (launch prerequisite only); M6 core has no current runtime failure evidence.
Demo3 manual diagnostics: `D3.CRASH.ABORT` and `D3.CRASH.NATIVE_THREAD`; neither is a closure gate. Build/unit/debug/release and arm64-v8a/x86_64 native gates PASS.
PROCESS_BINDING_GATE=PASS; NATIVE_BRIDGE_BOOTSTRAP_GATE=PASS; EARLY_LOAD_GATE=PASS; BIONIC_PATH_INTERCEPTION_PROVEN=YES.
M2_M3_M4_M5_M6_M7_M8_RUNTIME_REGRESSION=PASS; M9_NATIVE_IO_SCOPE=BIONIC_LIBC_MEDIATED_PATH_IO; DIRECT_SYSCALL_INTERCEPTION=OUT_OF_SCOPE.
API31=PASS; API36=PASS; M9_READY_TO_CLOSE=YES; M10_STARTED=NO; STOPPED_AFTER_M9_LIMIT_CORRECTION=YES.
