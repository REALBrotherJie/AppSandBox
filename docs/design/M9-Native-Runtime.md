# M9 Native Runtime

Guest native code is loaded through a GuestDomainClassLoader whose native search path is built from the
Guest APK's `lib/<device ABI>` entries. The selected ABI is the first intersection of `Build.SUPPORTED_ABIS`
and the APK inventory. Libraries are materialized once per package APK revision and ABI under the Host-owned
code cache; writable state remains under the VirtualInstance data root.

This preserves Guest ClassLoader ownership while avoiding the Host application's nativeLibraryDir. The same
directory lets Android's linker resolve DT_NEEDED dependencies and guest `dlopen`/`dlsym` requests. JNI_OnLoad,
dynamic JNI registration/calls, FindClass, attached native threads, and AAssetManager execute through the real
ART/native runtime. API31 selects arm64-v8a; API36 selects x86_64.

Direct libc/syscall path rewriting is intentionally separate from code loading. The current implementation does
not claim hardcoded absolute Guest data paths; that remains the M9 IO blocker and is reported as BLOCKED.
