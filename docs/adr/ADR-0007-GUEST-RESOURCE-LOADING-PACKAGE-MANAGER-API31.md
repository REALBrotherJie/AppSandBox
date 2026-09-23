# ADR-0007: Guest Resource Loading via PackageManager on API31

## Status

CONFIRMED FOR TESTED ANDROID 12 / API31 PATH ONLY

## Decision

For an uninstalled Guest APK stored in Host private storage, use public
`PackageManager.getPackageArchiveInfo()`, copy its `ApplicationInfo`, set
`sourceDir` and `publicSourceDir` to the Guest APK, and call
`PackageManager.getResourcesForApplication()`. The API31 overload accepting a
`Configuration` also passed the tested landscape read.

The fresh-process task-11 run resolved Guest strings, raw resources, assets,
colors, drawables, layout XML, metadata, identifiers, and configuration values.
It passed the defined two-way and Host/System pollution checks. This is not a
claim of perfect isolation or a security boundary.

## Scope and open research

Only Android 12/API31 behavior on the tested Xiaomi Mi 10 is confirmed.
API28, API29, API30, API32+, Android 13/14/15/16, simultaneous Guest
resources, lifecycle under pressure, configuration propagation, Activity
resources, themes, split APKs, dynamic features, and overlay APKs remain
RESEARCH NEEDED.
