# ADR-0008: Controlled Guest Context C0 on API31

## Status

CONFIRMED FOR DEBUG API31 HOST-PROCESS BASELINE

## Decision

On the tested Android 12/API31 device, a debug-only `ContextWrapper` can
provide a Guest-facing semantic baseline for an uninstalled Guest:

- logical Guest package name
- Guest DexClassLoader
- Option B Guest Resources and Assets
- Guest APK code/resource paths
- copied Guest ApplicationInfo with logical instance `dataDir`
- instance-scoped data, files, cache, code-cache, no-backup, `getDir`, and
  basic file API routing

The implementation remains an experiment. It does not create an installed
package or a Guest Application.

## Explicit non-claims

This ADR does not confirm Application, LoadedApk, separate UID, Binder or
AppOps identity, kernel filesystem isolation, PackageManager virtualization,
system-service virtualization, complete derived Context support, Android 9/10,
or Android 13+ behavior.

`getOpPackageName`, `AttributionSource`, PackageManager, ContentResolver,
system services, and derived Context APIs retain Host or deferred semantics.
The Host base Context is an acknowledged CRITICAL risk for EXP-003B.
