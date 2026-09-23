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

The most dangerous leaks are package name, paths, resources, class loader,
Application context, PackageManager, process identity, system-service
identity, and attribution source.

