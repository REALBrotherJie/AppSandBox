# EXP-003A Controlled Guest Context Result

## CURRENT STATUS

```text
CONFIRMED
```

On the Xiaomi Mi 10 running Android 12/API31, a debug-only
`ContextWrapper` baseline provided the defined C0 Guest-facing semantics:
logical package, Guest class loader, Option B resources/assets, Guest APK
paths, independent logical ApplicationInfo data path, and instance-scoped
file routing.

This does not confirm a Guest Application, LoadedApk, UID, Binder identity,
AppOps identity, SELinux boundary, kernel filesystem isolation, PackageManager
virtualization, system-service virtualization, or complete derived Context
support.

## Fresh process evidence

```text
PID = 12044
run count = 1
Guest installed = false
guestId = b238c693-816e-48c9-bcb3-116fe72b8378
instanceId = debug-exp003a-b238c693-816e-48c9-bcb3-116fe72b8378
```

## Core results

```text
Guest package = com.example.appsandbox.testguest
Host package = com.example.appsandbox
Guest loader = DexClassLoader
Host loader = PathClassLoader
GuestProbe via Context = PASS
GuestProbe via Host = ClassNotFoundException
Guest Resources = MiuiResources
Guest asset marker = exact PASS
Guest string marker = exact PASS
Guest sees Host resource/asset = false/false
Host/System pollution = none observed
```

All C0 storage getters and file APIs routed under the instance data root.
`openFileOutput` and `openFileInput` returned the complete marker
`EXP003A_FILE_6fd2c7a1-5b84-4e90-a316-8d2f7c9b4015`; Host's corresponding
default path remained absent. `getDir` also remained inside the instance root.

## Identity and delegation

`getOpPackageName()` and `AttributionSource` retained Host package/UID
semantics. `Process.myUid()` was `10293`; the Guest has no separate UID.
`getPackageManager()` could not resolve the uninstalled Guest package.
`ContentResolver` and the observed `LayoutInflater` service were Host-backed.
These are documented boundaries, not C0 failures.

The base Context was the Host `MainActivity`, explicitly recorded as:
`BASE_CONTEXT = HOST`. Derived configuration, device-protected, and attribution
contexts returned Host `ContextImpl`/Host package. Guest package-context lookup
threw `NameNotFoundException`. These are deferred escape risks.

## Error tests and API classification

Invalid APK path failed with `IllegalArgumentException`; missing instance data
was deterministically created; Host survived. The experiment used PUBLIC APIs
only, with no hidden API, reflection, Hook, Binder interception, JNI, or
native code.

## Gate interpretation

EXP-003A C0 is confirmed. Derived Context escape APIs are HIGH PRIORITY
DEFERRED before broad third-party Guest compatibility. EXP-003B remains
blocked by the existing LoadedApk precheck and was not executed.
