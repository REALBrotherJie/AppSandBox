# M9 Native IO Virtualization

## M9-FIX decision

The selected architecture is Guest-owned ELF relocation rebinding (GOT/PLT), not a global libc inline hook.
The process-side registry and policy model now carry package, instance, process slot, ABI, soname, physical path,
and DT_NEEDED metadata. A production native bridge still has to parse `DT_JMPREL`/RELA/REL, handle RELRO and
rebind the imported symbols listed below before Guest code executes. The Kotlin registry is deliberately not
treated as interception evidence.

The authoritative logical model is `Guest package + VirtualInstance -> instance dataRoot`, with canonical
containment under the instance root. Context-derived native paths already use this mapping naturally and are
validated by Demo3 open/read/write/stat/lstat/realpath/mmap tests.

The remaining hardcoded `/data/data/<guest>` and `/data/user/0/<guest>` class requires a native interception
boundary (PLT/GOT or libc-level wrappers) that can resolve the current process slot to a Guest instance and
rewrite every path-bearing operation consistently. No such clean-room interception layer existed at the M9
anchor, so this milestone does not silently fall back to Host paths and does not claim hardcoded-path PASS.

Direct syscalls were measured with Demo3's `SYS_openat` probe on API31 arm64-v8a and API36 x86_64: it bypasses
libc mediation and returned `ENOENT` for the logical Guest path. Therefore direct syscall interception is an
explicit unsupported boundary. System paths, `/proc`, `/dev`, `/system`, `/apex`, and Host
paths remain passthrough. Any future implementation must re-check canonical containment and symlink semantics
after rewriting, then add reverse logical results for readlink/realpath.
