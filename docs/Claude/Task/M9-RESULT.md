STATUS = PARTIAL
START_BRANCH = main
START_HEAD = c701dc5bf7bc7af1bc9141e8df4c35946ad3aed7
FINAL_HEAD = pending focused commit
COMMITS = native loader/materialization + IO policy + M8 planner provenance
Demo3 fixture = arm64-v8a + x86_64; libdemo3_native.so, libdemo3_dependency.so (DT_NEEDED), libdemo3_helper.so, JNI_OnLoad, JNI methods, native thread, assets, mmap, path probes.
NATIVE_ARCH = GuestDomainClassLoader + APK lib/<ABI> extraction; code is package/revision/ABI scoped, data remains instance scoped.
API31 ABI = arm64-v8a; API36 ABI = x86_64; both selected deterministically and logged as GUEST_NATIVE_LIBS.
LIBRARY_LOADING = PROVEN; JNI_ONLOAD = PROVEN; JNI_REGISTER_NATIVES = OBSERVED/fixture uses exported JNI symbols; JNI_CALL = PROVEN; NATIVE_THREAD = PROVEN.
DT_NEEDED = PROVEN; DLOPEN_DLSYM = PROVEN; GUEST_NATIVE_ASSET = PROVEN; CONTEXT_DERIVED_NATIVE_IO = PROVEN.
HARDCODED_GUEST_PATH_VIRTUALIZATION = NO; no native libc interception existed at anchor, so no silent Host fallback is used.
OPEN_OPENAT = PARTIAL (Context-derived open passes; hardcoded path interception not implemented); STAT_LSTAT = CONTEXT-DERIVED PASS; READLINK_REALPATH = CONTEXT-DERIVED PASS; MMAP = PROVEN.
NATIVE_INSTANCE_ISOLATION = CONTEXT-DERIVED PROVEN; NATIVE_RESTART_PERSISTENCE = NOT RUN for hardcoded native state; NATIVE_INSTANCE_LIFECYCLE = NOT PROVEN.
Physical native roots = /data/user/0/com.example.appsandbox/files/virtual/native/<package>/<apk-revision>/<abi>; writable roots remain VirtualInstance dataRoot.
API31 = PARTIAL; API36 = PARTIAL; M2_M3_M4_M5_M6_M7_M8_REGRESSION = PASS (Demo1 activity smoke and prior M8 framework evidence retained).
Known limitation = M9 hardcoded /data/data/<guest> and /data/user/0/<guest> requires clean-room libc/PLT interception with process-slot identity and canonical containment.
M9_READY_TO_CLOSE = NO
M10_STARTED = NO
STOPPED_AFTER_M9 = YES
