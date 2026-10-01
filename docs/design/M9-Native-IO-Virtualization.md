# M9 Native IO Virtualization

The authoritative logical model is `Guest package + VirtualInstance -> instance dataRoot`, with canonical
containment under the instance root. Context-derived native paths already use this mapping naturally and are
validated by Demo3 open/read/write/stat/lstat/realpath/mmap tests.

The remaining hardcoded `/data/data/<guest>` and `/data/user/0/<guest>` class requires a native interception
boundary (PLT/GOT or libc-level wrappers) that can resolve the current process slot to a Guest instance and
rewrite every path-bearing operation consistently. No such clean-room interception layer existed at the M9
anchor, so this milestone does not silently fall back to Host paths and does not claim hardcoded-path PASS.

Direct syscalls are outside the current coverage. System paths, `/proc`, `/dev`, `/system`, `/apex`, and Host
paths remain passthrough. Any future implementation must re-check canonical containment and symlink semantics
after rewriting, then add reverse logical results for readlink/realpath.
