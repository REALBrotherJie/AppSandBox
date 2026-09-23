# EXP-003A Delegation Inventory

## Status

This inventory records the API31 debug experiment. It is not a production
Context contract and does not imply Application or LoadedApk compatibility.

## Explicitly overridden and Guest-routed

- `getPackageName`
- `getClassLoader`
- `getResources`
- `getAssets`
- `getApplicationInfo`
- `getDataDir`
- `getFilesDir`
- `getCacheDir`
- `getCodeCacheDir`
- `getNoBackupFilesDir`
- `getPackageCodePath`
- `getPackageResourcePath`
- `getApplicationContext`
- `getFileStreamPath`
- `openFileInput`
- `openFileOutput`
- `deleteFile`
- `fileList`
- `getDir`

## Intentionally Host-delegated

- `getOpPackageName`
- `getAttributionSource`
- `getPackageManager`
- `getContentResolver`
- `getSystemService`
- `getMainLooper`
- permission and AppOps-sensitive APIs

Observed values remain Host identity: package `com.example.appsandbox`,
UID `10293`, and the Host process PID.

## Deferred

- SharedPreferences
- database APIs
- PackageManager virtualization
- ContentResolver virtualization
- system-service virtualization
- component launch and registration
- themes and LayoutInflater compatibility

## Dangerous implicit delegation

Any ContextWrapper method not explicitly listed above can delegate to the Host
base Context. In particular, storage, package identity, service access, and
derived Context APIs must not be assumed Guest-correct by default.

## Derived-context escape matrix

| API | API31 observation | Classification |
|---|---|---|
| `createConfigurationContext` | returns Host `ContextImpl`, Host package | HIGH PRIORITY DEFERRED / HOST ESCAPE |
| `createDeviceProtectedStorageContext` | returns Host `ContextImpl`, Host package | HOST ESCAPE |
| `createAttributionContext` | returns Host `ContextImpl`, Host package | IDENTITY-SENSITIVE / HOST ESCAPE |
| `createPackageContext(Guest)` | `NameNotFoundException` because Guest is uninstalled | DEFERRED |

