# System Service Compatibility

The host's Binder identity is the identity system services see. A guest facade may translate arguments and responses, but it cannot turn an ordinary host process into a new kernel identity.

## Levels

| Level | Meaning |
|---|---|
| 0 | Public API works without guest-specific state |
| 1 | argument/path/package translation only |
| 2 | return-value filtering or guest model conversion |
| 3 | complete logical service implementation required |
| 4 | unsupported or blocked by platform identity/security |

| Service | Initial level | Reason |
|---|---:|---|
| PackageManager | 3 | guest registry and visibility required |
| ActivityManager/ATMS | 3/4 | task and identity are system-owned |
| AppOps | 3/4 | tied to host UID/package and system policy |
| Notification | 2/3 | host channel/UID attribution and policy |
| Clipboard | 1/2 | host policy and user visibility |
| Storage | 1/2 | path translation plus host permission |
| Account | 4 initially | account ownership and authenticator identity |
| Location | 2/4 | host permission and attribution must be explicit |
| Connectivity | 1/2 | host network identity/policy |
| Activity/Window | 3/4 | system scheduler and window manager ownership |

`PROPOSED`: deny by default, add one API family at a time, and record whether behavior is native, adapted, or unavailable. No Binder hook is part of this design.
