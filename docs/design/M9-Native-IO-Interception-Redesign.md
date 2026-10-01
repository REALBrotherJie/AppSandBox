# M9 Native IO Interception Redesign

The primary M9 path interception architecture is `PROCESS_SLOT_SCOPED_EARLY_BIONIC_PATH_INTERCEPTION`.
Each `:pN` stub process preloads `libappsandbox_runtime.so`, binds `(guest package, instance, slot, CP/DP roots)`,
and installs process-local Bionic entry replacements before `GuestProcessBootstrap` creates a Guest ClassLoader.
Only exact `/data/data/<bound package>`, `/data/user/0/<bound package>`, and `/data/user_de/0/<bound package>`
namespaces are rewritten; physical instance paths and Host/system paths pass through. Wrappers execute internal
`SYS_openat` to avoid recursion, preserve flags/mode/errno, and reject a second binding in the same process.

The bridge currently hooks `open`, `openat`, `__open_2`, and `__openat_2` on arm64-v8a and x86_64. Entry patching
temporarily changes the containing page to RWX, writes an ABI-specific absolute jump, flushes the instruction cache,
and restores RX. The native bridge is loaded from the manifest stub provider before Guest native loading.
`HYBRID_RELOCATION_PLUS_GUEST_DLSYM` remains historical/secondary and is superseded for primary M9 early IO.
Direct Guest syscalls remain outside scope.
