# Security Model

AppSandbox is not an Android OS-level sandbox. Multiple guest executions may share the host UID, SELinux domain, process, and system-service attribution. Marketing and documentation must say logical isolation, not complete security isolation.

## Threats and controls

| Threat | Control |
|---|---|
| path traversal | canonical-root validation and opaque IDs |
| malicious APK/manifest | parse limits, validation, quarantine, digest |
| zip bomb | entry count/size/compression limits |
| native code | explicit warning, process boundary, ABI/path controls |
| exported component | guest resolver policy and no automatic host export |
| intent injection | typed resolver and caller policy |
| provider exposure | internal authority namespace and explicit grants |
| arbitrary file access | guest context path mapping and deny-by-default |
| host crash | future sandbox process and watchdog |
| confused deputy | preserve caller logical identity and capability checks |

Kernel isolation, unique UID, unique SELinux domain, and system-server policy cannot be emulated safely by ordinary application code. A malicious guest must be treated as code running with host-level application privileges.
