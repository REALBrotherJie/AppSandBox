START_BRANCH = main; START_HEAD = 38598279d899929e0ec4dab4841e4ce632bfbca7
FINAL_HEAD = pending focused commit; COMMITS = native bridge + early-load wiring + Demo3 probes
PROCESS_BINDING_GATE = PASS: StubProcessPool allocates one active instance per :pN; same-process conflicting bind rejected.
NATIVE_BRIDGE_BOOTSTRAP_GATE = PASS: API31/API36 p1 logs NATIVE_BRIDGE_PRELOADED and HOOK_INSTALL before Guest load.
EARLY_LOAD_GATE = PASS: constructor and JNI_OnLoad hardcoded writes both intercepted before/at System.loadLibrary.
API31 arm64-v8a = 24 PASS, 1 BLOCKED (direct syscall OUT_OF_SCOPE), 2 manual; API36 x86_64 = 24 PASS, 1 BLOCKED, 2 manual.
Evidence: PATH_REWRITE maps /data/user/0/com.reel.demo3/files/* to each instance files root; constructor/JNI_OnLoad files read PASS.
BIONIC anchors = open, openat, __open_2, __openat_2; arm64/x86_64 entry patch and RX restoration build/device PASS.
DT_NEEDED, dlopen, dlsym/open escape and context-derived IO retained PASS; Host/system paths are exact-namespace passthrough.
Two-instance concurrent slot isolation and process-slot rebind were not fully matrix-tested in this run; no PASS claim.
Restart/delete/recreate lifecycle and full M2-M8 device regression were not rerun after native hook insertion; no PASS claim.
Build = testDebugUnitTest PASS; assembleDebug PASS; assembleRelease PASS; native arm64-v8a/x86_64 builds PASS.
SELECTED_NATIVE_IO_ARCHITECTURE = PROCESS_SLOT_SCOPED_EARLY_BIONIC_PATH_INTERCEPTION
PREVIOUS_HYBRID_ARCHITECTURE_SUPERSEDED = YES; BIONIC_PATH_INTERCEPTION_PROVEN = YES
CONSTRUCTOR_HARDCODED_IO_INTERCEPTED = YES; JNI_ONLOAD_HARDCODED_IO_INTERCEPTED = YES; DLSYM_OPEN_ESCAPE_CLOSED = NOT_RUN
HARDCODED_GUEST_PATH_VIRTUALIZATION_PROVEN = YES; CONTEXT_DERIVED_NATIVE_IO_PROVEN = YES; DIRECT_SYSCALL_INTERCEPTION = OUT_OF_SCOPE
NATIVE_INSTANCE_ISOLATION_PROVEN = NOT_RUN; NATIVE_RESTART_PERSISTENCE_PROVEN = NOT_RUN; NATIVE_INSTANCE_LIFECYCLE_PROVEN = NOT_RUN
API31 = PARTIAL; API36 = PARTIAL; M2_M3_M4_M5_M6_M7_M8_REGRESSION = RETAINED PRIOR EVIDENCE ONLY
STATUS = PARTIAL; M9_READY_TO_CLOSE = NO; M10_STARTED = NO; STOPPED_AFTER_M9_REDESIGN = YES
