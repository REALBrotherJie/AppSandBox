# EXP-003B0 Host LoadedApk Impact Plan

## Status

DESIGN ONLY. Do not execute in task-13.

## Question

If a Guest Application is created with
`Instrumentation.newApplication(GuestLoader, GuestApplication,
ControlledContext)`, which observations remain Guest-correct and which reveal
Host `LoadedApk`/ContextImpl state?

## Hypothesis

`Application.attach()` will preserve the Controlled Context as the base
Context for ordinary overridden getters, while `Application.mLoadedApk` will
be populated from the Host bottom `ContextImpl`. Framework-derived operations
will split into Base Context-dominant and LoadedApk-sensitive groups.

## Required observations

Do not call `onCreate`. Record Guest Application class/loader, base Context,
package/resources/assets/class loader/ApplicationInfo/files path,
`getApplicationContext`, op-package/AttributionSource, PackageManager,
ContentResolver, component callbacks, system services, and derived Contexts.

Internal observations require explicit approval and hidden observation:

- `Application.mLoadedApk`
- `LoadedApk.mPackageName`
- `LoadedApk.getApplicationInfo()`
- `LoadedApk.getClassLoader()`
- `LoadedApk.getResources()`
- `ContextImpl.mPackageInfo`
- ActivityThread `mPackages` and `mResourcePackages`

No reflection should be added until that approval exists.

## Negative controls

- Host loader cannot load the Guest Application.
- Guest C0 getters are compared with Application getters.
- Application `getApplicationContext()` is compared with controlled bootstrap
  Context and Host Application.
- No `onCreate`, component launch, Binder interception, or service hook.

## Exit criteria

1. Each observation is classified Base Context dominant, LoadedApk sensitive,
   Host identity reality, deferred, or unsupported.
2. Hidden observations are approved or explicitly deferred.
3. Same-package two-instance cache behavior has a separate test design.
4. No conclusion upgrades EXP-003B to ready for EXP-003C automatically.
