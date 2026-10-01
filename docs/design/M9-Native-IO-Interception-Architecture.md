# M9 Native IO Interception Architecture Decision

## Evidence

Demo3 was audited on API31 arm64-v8a and API36 x86_64. Both ELF files are dynamically linked and contain
`PT_GNU_RELRO`; both import `open`, `__open_2`, `stat`, `lstat`, `realpath`, and `dlsym`. The relocation types
are `R_AARCH64_JUMP_SLOT/GLOB_DAT` on arm64 and `R_X86_64_JUMP_SLOT/GLOB_DAT` on x86_64. `DT_NEEDED` includes
the Guest dependency chain. Runtime `dl_iterate_phdr` enumerated Guest modules and RELRO on both devices.

## Decision

Select **HYBRID_RELOCATION_PLUS_GUEST_DLSYM**:

1. Bind one physical process slot to one immutable `(Guest package, VirtualInstance, processSlot)` context.
2. Before Guest constructor/JNI_OnLoad execution, register the loaded Guest ELF and all DT_NEEDED modules,
   parse dynamic relocation tables, temporarily mprotect RELRO pages, rebind imported path APIs, then restore
   original page protections. This must be integrated into the controlled native-load boundary, not after
   `System.loadLibrary` returns.
3. Rebind Guest `dlsym` as a secondary escape closure. Requests for path-bearing symbols return the same wrappers;
   unrelated symbols delegate to the real linker. New `dlopen` modules enter the same registry before Guest code
   is allowed to run, with a constructor-timing gate.
4. Wrappers call the existing NativeIoPolicy, preserve errno/varargs/fortify semantics, and invoke real libc only
   after exact Guest path classification and containment. Host/runtime modules are never patched.

## Candidate decisions

- Guest-only GOT/PLT rebinding is the isolation primitive, but post-load-only patching is insufficient for early
  constructor/JNI_OnLoad IO.
- Process-wide libc hook + caller-PC classification is rejected as primary: caller PCs can cross helper/system
  DSOs, internal libc calls bypass the boundary, and Host contamination/recursion risk is materially higher.
- Linker namespace interposition is rejected: Android namespace resolution still binds ordinary Guest imports to
  Bionic libc; no reliable Guest-only override was observed.
- Direct `SYS_openat` bypass remains OUT_OF_SCOPE; neither relocation nor libc interception claims syscall coverage.

## Required implementation boundary

The next implementation must provide a pre-JNI load callback, ELF registry, REL/RELA/JMPREL parser, RELRO-safe
patch/restore, fortify variants, `dlsym`/`dlvsym` handling, new-dlopen registration, and Host non-interference.
No production broad libc hook or kernel/syscall emulation is implied by this decision.
