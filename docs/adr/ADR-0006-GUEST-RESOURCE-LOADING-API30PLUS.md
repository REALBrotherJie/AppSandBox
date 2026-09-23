# ADR-0006: Guest Resource Loading on API 30+

## Status

SUPERSEDED / INVALIDATED

## Context

AppSandbox must load resources from an APK stored in its private directory
without installing the Guest package. The previous experiment incorrectly used
the Host `AssetManager` as the base resource space.

## Decision

On the tested Android 12 / API 31 device, both of these public paths work:

1. `Resources.getSystem().assets` plus `ResourcesLoader` and
   `ResourcesProvider.loadFromApk()`.
2. `PackageManager.getPackageArchiveInfo()` followed by a copied
   `ApplicationInfo` and `getResourcesForApplication()`.

The Option A conclusion is invalid. `Resources.getSystem().assets` is shared
System state and `Resources.addLoaders()` mutates that shared AssetManager.
The path therefore cannot be an isolated Guest resource architecture.

Option A is rejected and must not be used for Guest runtime or as a fallback.
Option B is evaluated separately in the task-11 fresh-process revalidation.

## Scope

This ADR confirms only the tested API 31 resource-loading capability.

It does not confirm:

- Android 9 or Android 10 compatibility
- Guest Context or Theme behavior
- Guest Application or Activity execution
- LayoutInflater with Guest Context
- Activity resources or configuration propagation
- production runtime lifecycle design

No EXP-003 implementation is included.
