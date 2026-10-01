# M9 Limit Review Result
START_BRANCH = main; START_HEAD = 0802fa7e0f1e9b5c5998faa8c4a42f0f53e7c49a; FINAL_HEAD = 4750a8f8f39778ab7315efb3b9eb43bf96dc40f4
D1.DATA.APP_DIRS exact contract = filesDir/cacheDir/codeCacheDir/noBackupFilesDir/databaseDir/getDir all canonical-contained by Context.dataDir, plus write/read.
API31/API36 current actuals: files/cache/noBackup/database PASS; codeCacheDir=`/data/user/0/com.example.appsandbox/code_cache` FAIL; getDir=`/data/user/0/com.example.appsandbox/app_demo1_custom` FAIL; writes still PASS.
Historical M5 evidence had codeCacheDir and getDir under `/data/data/com.example.appsandbox/files/virtual/instances/<instance>` and PASS.
Primary classification = RUNTIME_REGRESSION; root cause = OTHER: Guest Context/Application directory synthesis omits instance root for codeCacheDir/getDir. This is not demonstrated as native path rewrite.
Hook ON evidence = current bridge installed; exact per-operation log shows no interception for those Java-returned paths. Physical files/cache/database paths pass through unchanged.
Hook OFF controlled comparison = NOT RUN: current production bootstrap has no diagnostic OFF switch; historical no-native M5 PASS is retained only as comparison, not a controlled same-HEAD experiment.
Reverse mapping audit: realpath/readlink are guarded to reverse-map only logical Guest input; current failing paths are already Host physical paths, so reverse mapping is not implicated by observed evidence.
M7 storage smoke = PASS on both devices; M8 provider focused smoke = NOT_RUN in this review. Existing M8 closure evidence remains unchanged.
M4 classification = RUNTIME_PASS_HARNESS_BLOCKED: Activity behavior has independent PASS evidence; current blocked result is launch prerequisite/automation state, not a proven Guest lifecycle regression.
Process lifecycle model remains UNBOUND→BINDING→BOUND(instance)→RUNNING→TEARDOWN→UNBOUND; native hook lifetime is process-scoped, binding publication is lock-protected.
Concurrent dual-slot isolation = UNVERIFIED: MainActivity shell launches were delivered to the existing top activity, so no authoritative p1/p2 concurrent proof was produced.
Explicit same-slot rebind = UNVERIFIED; deletion showed registry/storage cleanup, but no clean p1 Instance0→teardown→p1 Instance1 native proof was obtained. Stale binding absence therefore UNVERIFIED.
LIMIT_REVIEW_STATUS = PARTIAL; M9_STATUS = PARTIAL; NATIVE_INTERCEPTION_M5_COMPATIBILITY = CONDITIONAL.
PHYSICAL_INSTANCE_PATH_PASSTHROUGH_PROVEN = YES; REVERSE_MAPPING_ISOLATION_PROVEN = YES; API31 = PARTIAL; API36 = PARTIAL.
CONCURRENT_NATIVE_INSTANCE_ISOLATION = UNVERIFIED; PROCESS_SLOT_REBIND_PROVEN = NO; STALE_NATIVE_BINDING_PROVEN_ABSENT = NO.
M9_READY_TO_CLOSE = NO; M10_STARTED = NO; STOPPED_AFTER_LIMIT_REVIEW = YES.
RECOMMENDED_ACTION = A. FOCUSED_IMPLEMENTATION_CORRECTION (fix Guest codeCacheDir/getDir context mapping, then independently validate lifecycle); no implementation started here.
