# EXP-003 Controlled Guest Context Contract

## Contract Boundary

```text
Guest Application
        |
        v
Controlled Guest Context
        |
  +-----+------------------+
  |                        |
Guest-owned             Host-backed
semantics               delegation
```

Every unoverridden `ContextWrapper` method delegates to the Host base Context,
so delegation is a deliberate compatibility decision, not a neutral default.

## Tiers

### C0: Must Be Guest-correct

`getPackageName`, `getClassLoader`, `getResources`, `getAssets`,
`getApplicationInfo`, `getFilesDir`, `getCacheDir`, `getCodeCacheDir`,
`getDataDir`, and `getNoBackupFilesDir`.

### C1: Host-backed with explicit disclosure

`getMainLooper`, selected safe `getSystemService`, and the bootstrap
`getApplicationContext`. Returned objects must not be represented as Guest
identity unless separately validated.

### C2: Requires virtualization or a narrow wrapper

`getPackageManager`, `getSharedPreferences`, `getDatabasePath`,
`openOrCreateDatabase`, and `getContentResolver`.

### C3: Identity-sensitive

`getOpPackageName`, `getAttributionSource`, `checkPermission`, AppOps,
notifications, accounts, permission checks, and any service call carrying
package name or UID. Preserve the real Host identity and never fabricate a
Guest UID.

### C4: Deferred or unsupported

`startActivity`, `startService`, `bindService`, provider binding, component
callbacks with system semantics, WebView, native loading, and third-party SDK
initialization.

## Capability Matrix

| API | Desired Guest semantics | Delegate Host? | Override/virtualize | Identity-sensitive | Scope |
|---|---|---:|---:|---:|---|
| `getPackageName` | Guest package | no | yes | yes | C0 |
| `getOpPackageName` | explicit Host reality | no | yes | yes | C3 |
| `getAttributionSource` | no fake UID | no | yes | yes | C3 |
| `getApplicationInfo` | logical Guest view | no | yes | yes | C0 |
| `getClassLoader` | Guest loader | no | yes | no | C0 |
| `getResources` | Option B Guest resources | no | yes | no | C0 |
| `getAssets` | Guest assets | no | yes | no | C0 |
| `getTheme` | unvalidated | no | later | yes | C4 |
| `getFilesDir` | instance files | no | yes | no | C0 |
| `getCacheDir` | instance cache | no | yes | no | C0 |
| `getCodeCacheDir` | instance code cache | no | yes | no | C0 |
| `getDataDir` | instance data root | no | yes | no | C0 |
| `getNoBackupFilesDir` | instance no-backup | no | yes | no | C0 |
| `getDatabasePath` | instance database path | no | later | no | C2 |
| `getSharedPreferences` | instance preferences | no | later | no | C2 |
| `getPackageManager` | narrow Guest view | no | later | yes | C2 |
| `getContentResolver` | deferred | no | later | yes | C2 |
| `getSystemService` | allowlist only | selective | yes | often | C1/C3 |
| `checkPermission` | Host UID truth | no | yes | yes | C3 |
| `startActivity` | no implicit Host launch | no | later | yes | C4 |
| `startService` | deferred | no | later | yes | C4 |
| `bindService` | deferred | no | later | yes | C4 |
| `registerReceiver` | deferred | no | later | yes | C4 |
| `sendBroadcast` | deferred | no | later | yes | C4 |

## Bootstrap Application Context

Before an Application exists, `getApplicationContext()` should return the
bootstrap Controlled Context, never the Host Application. After attachment it
should return the Guest Application only for the same Guest Instance. This
requires an explicit two-phase binding:

```text
BootstrapContext -> Application attach -> BoundGuestContext
```

This is **PROPOSED**, not implemented.

