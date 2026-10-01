STATUS = PARTIAL
START_BRANCH = main
START_HEAD = a33887261c4e37e2cda1809ec1f356cd1861e798
FINAL_HEAD = pending focused commit
COMMITS = Guest ELF registry/policy hardening + M9-FIX evidence/design
SELECTED_ARCHITECTURE = GUEST_ELF_RELOCATION_REBINDING (Guest-owned GOT/PLT only; native bridge not yet implemented)
GUEST_ELF_REGISTRY = model added; no relocation patch count claimed
ABI = API31 arm64-v8a; API36 x86_64; prior loader/JNI/DT_NEEDED/dlopen/thread/assets remain PASS
NATIVE_IO_POLICY = exact /data/data, /data/user/0, /data/user_de prefixes; instance-root containment; physical-root passthrough
HARDcoded_PATH = API31/API36 BLOCKED: libc open on /data/user/0/com.reel.demo3/... returns ENOENT; no Host fallback
DLSYM_IO_ESCAPE_CLOSED = NO (no native dlsym wrapper/rebinding bridge)
DIRECT_SYSCALL_INTERCEPTION = NOT_SUPPORTED (measured SYS_openat probe on both ABIs; NOT_VIRTUALIZED/ENOENT)
M9_NATIVE_IO_SCOPE = LIBC_MEDIATED_PATH_IO (not yet closed; context-derived IO remains PASS)
API31 = PARTIAL; API36 = PARTIAL; M2_M3_M4_M5_M6_M7_M8_REGRESSION = PASS by retained evidence and loader regression
BUILD = testDebugUnitTest PASS; assembleDebug PASS; assembleRelease PASS
KNOWN_LIMITATION = Guest-owned ELF parsing/RELRO-safe GOT rebinding and wrappers for open/openat/fortify/stat/lstat/readlink/realpath/mutation remain unimplemented.
GUEST_NATIVE_LIBRARY_LOADING_PROVEN = YES; GUEST_ABI_SELECTION_PROVEN = YES; GUEST_NATIVE_LIBRARY_DIR_PROVEN = YES
JNI_ONLOAD_PROVEN = YES; JNI_REGISTER_NATIVES_PROVEN = YES; JNI_CALL_PROVEN = YES; NATIVE_THREAD_PROVEN = YES
DT_NEEDED_PROVEN = YES; DLOPEN_DLSYM_PROVEN = YES; GUEST_NATIVE_ASSET_PROVEN = YES; CONTEXT_DERIVED_NATIVE_IO_PROVEN = YES
HARDCODED_GUEST_PATH_VIRTUALIZATION_PROVEN = NO; NATIVE_OPEN_OPENAT = NO; NATIVE_STAT_LSTAT = NO; NATIVE_READLINK_REALPATH = NO; NATIVE_MMAP = NO
NATIVE_INSTANCE_ISOLATION = NO; NATIVE_RESTART_PERSISTENCE = NO; NATIVE_INSTANCE_LIFECYCLE = NO; GUEST_ELF_IO_INTERCEPTION_PROVEN = NO
M9_READY_TO_CLOSE = NO; M10_STARTED = NO; STOPPED_AFTER_M9_FIX = YES
