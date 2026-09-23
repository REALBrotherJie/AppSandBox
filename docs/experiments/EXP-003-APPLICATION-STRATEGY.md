# EXP-003 Application Strategy

## Options

### Option A: Public Instrumentation Path

```text
Guest ClassLoader
+ Controlled Guest Context
+ guest Application class name
        |
        v
Instrumentation.newApplication(...)
```

Status: **PROPOSED BUT BLOCKED BY LOADEDAPK SEMANTICS**. Public API, explicit
ClassLoader and Context inputs, and an observable attach step. It still does
not provide a real PMS record, UID, process, or system-service identity.
`Application.attach()` calls through `ContextImpl.getImpl()`, which unwraps
ContextWrapper and can leave the Application holding Host `LoadedApk` state.
This must be prechecked before EXP-003B execution.

Task-13 keeps this candidate blocked pending the API31 AOSP dependency
matrix and the future `EXP-003B0-HOST-LOADEDAPK-IMPACT-PLAN.md`. It is not a
production recommendation.

### Option B: Direct Object Construction

Load the Guest Application class with the Guest ClassLoader and call its
constructor. This is a negative/control comparison. Because Application
extends ContextWrapper, construction alone does not provide a usable base
Context or attached Application state. It must not be treated as runtime
success.

### Option C: Framework Internal Creation

Use the conceptual `ActivityThread` / `LoadedApk` / `ContextImpl` path. This is
for future compatibility research only. It involves framework internals and
hidden implementation assumptions, so it is not called, reflected, or copied
in EXP-003A/B/C.

## Proposed Order

1. EXP-003A validates the Controlled Context without an Application.
2. EXP-003B validates `Instrumentation.newApplication` without `onCreate`
   only after the LoadedApk precheck passes.
3. EXP-003C invokes a minimal `onCreate` only after A and B pass.
4. EXP-003D, if needed, expands compatibility one capability family at a time.

## API Baseline

| API | Status | Purpose |
|---|---|---|
| `Instrumentation.newApplication` | PUBLIC | Application instantiation |
| `ContextWrapper` | PUBLIC | Experimental Context candidate |
| `PackageManager.getPackageArchiveInfo` | PUBLIC | Archive metadata |
| `PackageManager.getResourcesForApplication` | PUBLIC | API 31 resource baseline |
| `DexClassLoader` | PUBLIC | Guest code space |

`ActivityThread`, `LoadedApk`, and `ContextImpl` are framework implementation
details for this design round.
