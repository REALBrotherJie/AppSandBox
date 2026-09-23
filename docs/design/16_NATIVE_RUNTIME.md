# Native Runtime Design

Native libraries may be under `lib/<abi>/`. Loading involves class-loader native search paths, `Runtime.nativeLoad`, `System.loadLibrary`, `dlopen`, and `JNI_OnLoad`.

`PROPOSED` flow:

1. inspect archive entries and supported ABIs;
2. choose ABI using platform-supported ABI order;
3. extract only validated native entries to an instance/revision-controlled directory;
4. apply size/path limits and verify digest;
5. expose the directory through the guest loader's native search path;
6. serialize load and unload policy.

Same-named libraries from different guests must never share a mutable path. Prefer immutable revision extraction plus instance-specific writable state. Native code remains host-process code with host privileges; no native hook or ELF rewriting is planned.

`RESEARCH NEEDED`: public API support for the required native search path, linker namespace behavior on API 28-36, JNI class-loader identity, 32/64-bit process constraints, and cleanup while libraries are loaded.
