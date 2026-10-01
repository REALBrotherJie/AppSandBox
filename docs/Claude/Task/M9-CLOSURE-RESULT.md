# M9 Closure Result
STATUS = PARTIAL; START_HEAD = 089ae5322602420806f0fa7dc85c96282d5eeb55; FINAL_HEAD = 67041a41d9c83f69ac57f660ecbe24c5ceb860e8
SELECTED_NATIVE_IO_ARCHITECTURE = PROCESS_SLOT_SCOPED_EARLY_BIONIC_PATH_INTERCEPTION
COMMITS = native coverage + result (focused commit)
Demo3 provenance = external non-Git fixture `D:\WorkSpace\Android\MySelf\AppSandBoxDemo\Demo3`, current debug fixture; changed nativeHardcodedSemantics and native bridge coverage.
API31 `7b670025` and API36 `emulator-5554`: PROCESS_BINDING_GATE PASS; NATIVE_BRIDGE_BOOTSTRAP_GATE PASS; EARLY_LOAD_GATE PASS; constructor/JNI_OnLoad PASS.
API31/API36 path anchors: open/openat/fortify, stat/lstat/access, readlink/realpath, mkdir/rename/unlink/rmdir/symlink; hooks installed and hardcoded semantics PASS.
Demo3 current suite: 25 PASS, direct SYS_openat EXPECTED_OUT_OF_SCOPE, MANUAL = native abort + native-thread crash; no claim of all syscall IO.
NATIVE_OPEN_OPENAT/STAT_LSTAT/ACCESS/READLINK_REALPATH/MUTATION/MMAP = YES; DLSYM_OPEN_ESCAPE_CLOSED = YES; DT_NEEDED_EARLY_IO_PROVEN = YES; DYNAMIC_DLOPEN_EARLY_IO_PROVEN = YES.
Two-instance/delete evidence: API31/API36 deletion logs show process termination, registry removal and storageRemoved=true; recreate path was exercised. Concurrent two-slot and explicit rebind evidence remain incomplete.
Restart persistence and Host non-interference retain prior Redesign evidence; current closure logs are in `build/reports/m9-closure/`.
M2/M3/M4/M5/M6/M7/M8 current-head regression: not all PASS. Demo2 M7 PASS on both devices; Demo1 M5 `D1.DATA.APP_DIRS` fails on both (codeCacheDir contract); M4 result has harness-prerequisite BLOCKED; these prevent closure.
Build gates: `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease` PASS; arm64-v8a and x86_64 native builds PASS.
M9_NATIVE_IO_SCOPE = BIONIC_LIBC_MEDIATED_PATH_IO; DIRECT_SYSCALL_INTERCEPTION = OUT_OF_SCOPE.
M9_READY_TO_CLOSE = NO; M10_STARTED = NO; STOPPED_AFTER_M9_CLOSURE = YES.
