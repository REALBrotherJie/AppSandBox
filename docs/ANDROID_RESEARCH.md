# Android research

This file records the initial Phase 1 research. The expanded, status-labeled research baseline is [design/01_ANDROID_APP_RUNTIME_MODEL.md](design/01_ANDROID_APP_RUNTIME_MODEL.md) and [design/25_EXPERIMENT_PLAN.md](design/25_EXPERIMENT_PLAN.md).

## Parse an uninstalled APK

Problem: obtain package metadata without installing a guest package.

Android API: `PackageManager.getPackageArchiveInfo()` with component flags.

AOSP reference: `android.content.pm.PackageManager` and `PackageParser` are the platform boundary used by PackageManager for archive parsing.

Experiment: the app copies the selected document into its private files directory, points `ApplicationInfo.sourceDir` and `publicSourceDir` at that archive, and reads metadata through the platform API.

Our design: `GuestPackageReader` owns this operation and returns a small independent record. Parser failures are surfaced to the UI and the temporary guest directory is removed.

## Select user-owned documents

Problem: read an APK without broad storage permissions.

Android API: Storage Access Framework using `ACTION_OPEN_DOCUMENT`, `CATEGORY_OPENABLE`, and the APK MIME type.

Our design: obtain a resolver stream from the returned URI and close it with Kotlin `use`.
