# ADR-0006: Guest Resource Loading on API 30+

## Status

Accepted as an experimental API 30+ baseline.

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

Both paths resolved Guest numeric resource IDs, strings, raw resources, assets,
colors, drawables, configuration-qualified values, and compiled layout XML
without exposing Host-only resources or assets.

The preferred candidate for further design review is Option B because it
avoids the deprecated `Resources(AssetManager, DisplayMetrics, Configuration)`
constructor. Option A remains a useful API 30+ baseline and diagnostic path.

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
