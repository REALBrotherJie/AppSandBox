# Guest Runtime Gap Analysis

| Capability | Installed app | Uninstalled guest | Gap | Required strategy |
|---|---|---|---|---|
| Package metadata | PMS | Registry only | system services do not know package | logical registry |
| UID/appId | unique UID | host UID | kernel identity is shared | logical identity; disclose limit |
| SELinux domain | per-app domain on modern releases | host domain | no per-guest MAC | process/storage policy; not equivalent |
| APK path | system-managed code path | private copied file | runtime path not registered | controlled archive paths |
| dataDir | `/data/user/<u>/<pkg>` | none | Context paths absent | per-instance private directories |
| code cache | system-managed | none | code cache absent | own cache; verify platform restrictions |
| Activity registration | PMS/ATMS | absent | system cannot resolve guest | host-facing launch adapter plus resolver |
| Service registration | PMS/AMS | absent | AMS cannot schedule guest service | logical service controller |
| Receiver registration | PMS/AMS | absent | static delivery unavailable | internal dispatcher; limited system delivery |
| Provider authority | PMS/AMS | absent | authority lookup fails/collides | logical authority namespace |
| Resources | LoadedApk/ResourcesManager | archive only | no guest Resources object | per-guest Resources experiment |
| Class loader | app PathClassLoader | host loader | guest classes absent | isolated DexClassLoader experiment |
| Application | ActivityThread | absent | no standard attach lifecycle | controlled lifecycle only after experiments |
| PackageManager calls | PMS-backed | package not found | guest sees host/system view | compatibility facade where safe |
| ActivityManager/ATMS | system scheduler | host identity | task/process mismatch | explicit launch strategy; version-sensitive |
| Notifications | UID/package validation | host identity/package absent | policy and attribution mismatch | support matrix; likely host-backed |
| Native libraries | installed ABI paths | archive entries | search and extraction absent | controlled extraction and ABI policy |
| Permissions | PMS + AppOps + UID | no grant state | host/guest state conflated | guest state mapped to host capabilities |
| App links/intents | system resolver | absent | implicit resolution incomplete | guest resolver plus explicit boundary |
| Backup/restore | system package state | absent | no standard integration | non-goal initially |
| Work profile/user state | framework user/package state | absent | user lifecycle unavailable | instance state only |
| Notifications/services in background | system quotas and policy | host app policy | guest attribution unavailable | host policy, documented limitations |
| Shared libraries | package dependency graph | archive-local | dependency resolution uncertain | inspect/declare dependencies |
| Signing identity | PMS signing details | parsed APK signing info only | not trusted install identity | verification metadata, no impersonation |
| Install source | PackageInstaller | user-selected URI | no system installer record | record provenance locally |

## Consequence

The architecture must not promise “another installed app inside an app.” It provides a guest execution environment layered on an installed host. The strongest guarantees are logical package/storage/component separation. Kernel UID, SELinux, system-server attribution, and platform-enforced lifecycle remain host or unsupported.

## Priority

1. Package and storage correctness.
2. Class/resource loading isolation.
3. Explicit component lifecycle.
4. System-service compatibility with deny-by-default policy.
5. Activity integration only after a versioned experiment proves a public-path design.
