# EXP-003 Host Leakage Matrix

| Surface | Expected Guest | Expected Host reality | Acceptance |
|---|---|---|---|
| package name | Guest logical package | Host package at system boundary | Must fix Guest getter |
| op package name | explicit policy | Host caller identity | Identity-sensitive |
| UID/PID | none fabricated | Host UID/PID | Must preserve |
| class loader | Guest loader | Host loader | Must isolate |
| resources/assets | Guest resources | Host resources | Must isolate |
| ApplicationInfo | logical Guest view | PMS/archive reality | Must document |
| data/files/cache paths | Guest instance paths | Host private paths | Must isolate |
| PackageManager | narrow Guest view | PMS Host caller | Deferred |
| ContentResolver | deferred | Host provider boundary | Deferred |
| system services | allowlisted | Host identity | Per-service audit |
| process name | Host process | Host process | Must disclose |
| attribution source | no fake UID | Host attribution | Identity-sensitive |
| shared preferences | instance scope | Host storage implementation | Future |
| database | instance scope | Host SQLite implementation | Future |
| Activity/service launch | no implicit Host launch | Host component system | Unsupported initially |
| `Application.mLoadedApk` | Guest-specific runtime state | Host `LoadedApk` may be retained | **CRITICAL** |
| `Application.attach` / `ContextImpl.getImpl` | Guest bottom context | ContextWrapper unwrap can reach Host ContextImpl | **CRITICAL** |
| `getOpPackageName` | Guest-facing package if virtualized | Host package | HOST IDENTITY REALITY |
| `AttributionSource` | Guest attribution if virtualized | Host package and UID | HOST IDENTITY REALITY |
| `getApplicationInfo` | Guest logical copy | Host metadata unless overridden | C0 must override |
| `getDataDir` / file APIs | Guest instance root | Host paths by default | C0 must override |
| `getPackageCodePath` / resource path | Guest private APK | Host APK by default | C0 must override |
| `getApplicationContext` | Controlled bootstrap context | Host Application by default | C0 must override |
| derived Context APIs | Guest-preserving context | Host ContextImpl escape | HIGH PRIORITY DEFERRED |
| `ContextImpl.mPackageInfo` | Guest LoadedApk | Host LoadedApk | **CRITICAL** |
| `LoadedApk.getApplication()` | Guest Application | Host Application/cache | **CRITICAL** |
| `LoadedApk.getClassLoader()` | Guest loader | Host loader | **CRITICAL** |
| `LoadedApk.getResources()` | Guest resources | Host resources | **CRITICAL** |
| `LoadedApk.getApplicationInfo()` | Guest metadata | Host metadata | **CRITICAL** |
| `ActivityThread` package cache | per-instance state | package-keyed cache collision | **CRITICAL** |
| application context lookup | Guest bootstrap/Application | Host `mPackageInfo.getApplication()` | **CRITICAL** |

The most dangerous leaks are package name, paths, resources, class loader,
Application context, PackageManager, process identity, system-service
identity, and attribution source.
