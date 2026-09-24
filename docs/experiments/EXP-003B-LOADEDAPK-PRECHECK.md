# EXP-003B LoadedApk / ContextImpl Precheck

## Status

`READY FOR B0-P (PUBLIC-ONLY); HIDDEN OBSERVATION DEFERRED`

Historical task-13 scope was research and design only. No Guest Application was created and
none of `Instrumentation.newApplication()`, `Application.attach()`, or
`Application.onCreate()` was called.

## API31 creation chain

```text
LoadedApk.makeApplication()
  -> Instrumentation.newApplication(Class, Context)
  -> Application.attach(Context)
  -> Application.attachBaseContext(Context)
  -> ContextImpl.getImpl(Context)
  -> unwrap ContextWrapper chain
  -> ContextImpl.mPackageInfo
  -> Application.mLoadedApk
```

The Android 12 framework path can therefore produce Guest-facing overridden
getters while retaining Host `LoadedApk` state in the Application. With the
EXP-003A base chain, the bottom object is Host `ContextImpl`.

## API31 AOSP evidence

| File | Method/field | Finding |
|---|---|---|
| `Instrumentation.java` | `newApplication` | Instantiates the class and calls `app.attach(context)` |
| `Application.java` | `attach` | Calls `attachBaseContext` and assigns `mLoadedApk` from `ContextImpl.getImpl(context).mPackageInfo` |
| `ContextImpl.java` | `getImpl` | Unwraps ContextWrapper until the underlying ContextImpl |
| `ContextImpl.java` | `mPackageInfo` | Stores the LoadedApk used by normal ContextImpl operations |
| `LoadedApk.java` | `makeApplication` | Builds/caches Application, obtains class loader, creates app Context, optionally calls onCreate |
| `ActivityThread.java` | `getPackageInfoNoCheck` | Creates/gets LoadedApk through internal package caches |

## Android 12-16 comparison

| Version | newApplication | Application.attach | ContextImpl.getImpl | mLoadedApk/cache |
|---|---|---|---|---|
| Android 12/API31 | Present | Same attachBaseContext and mPackageInfo dependency | ContextWrapper unwrapping | Present |
| Android 13/API33 | Same architectural chain | Same semantic dependency | Same role | Present |
| Android 14/API34 | Same public entry | Same semantic dependency | Same role | Present |
| Android 15/API35 | Same architecture | Same semantic dependency | Same role | Present |
| Android 16/QPR2 | Same public entry/internal boundary | Same semantic dependency | Same role | Present |

Signatures and flags evolve, but the reviewed versions retain framework-created
`LoadedApk` and `ContextImpl` as the Application runtime boundary.

## Dependency matrix

| Behavior | Base Context | `Application.mLoadedApk` | `ContextImpl.mPackageInfo` | Risk |
|---|---:|---:|---:|---|
| package/resources/assets/class loader getters | dominant when overridden | indirect | direct in normal ContextImpl | Host leak if bypassed |
| ApplicationInfo and data paths | dominant when overridden | indirect | direct | CRITICAL |
| `getApplicationContext` | wrapper override can dominate | `LoadedApk.getApplication()` | `mPackageInfo.getApplication()` | CRITICAL |
| component callbacks | Application callback lists | Application state | framework dispatch | LOADEDAPK-SENSITIVE |
| receiver registration | ContextImpl | receiver dispatcher | `mPackageInfo` dispatcher | LOADEDAPK-SENSITIVE |
| service/activity launch | ContextImpl plus identity | indirect | framework identity | IDENTITY-SENSITIVE |
| PackageManager/ContentResolver | ContextImpl | indirect | Host package identity | HOST-BACKED |
| system services | ContextImpl | indirect | managers may capture Context | CRITICAL |
| configuration/memory callbacks | Context/Application | Application state | framework lifecycle | LOADEDAPK-SENSITIVE |

## LoadedApk contents

API31 `LoadedApk` carries or derives package name, `ApplicationInfo`, base and
split code/resource paths, class loader, resources, native/library paths,
compatibility information, component factory/application class information,
Application state, receiver dispatchers, and data-directory semantics. It is
not merely bookkeeping.

## Consequences of Host LoadedApk

Package metadata, class loader, resources/assets, ApplicationInfo,
application-context lookup, system-service managers, receiver/service
dispatch, callbacks, and same-package cache behavior can re-enter Host
semantics. A Controlled Context override only covers calls made through that
wrapper; it cannot rewrite framework-held `mPackageInfo`.

## ContextImpl and public APIs

`createPackageContext()` and related public APIs normally ask ActivityThread for
a framework LoadedApk associated with an installed/PMS-visible package.
An uninstalled Guest therefore fails at package lookup. Configuration contexts
derive from the current Host ContextImpl and do not create Guest LoadedApk.
`PackageManager` archive resource loading provides Resources, not a complete
Application runtime.

`ActivityThread.getPackageInfoNoCheck()` can create or retrieve a LoadedApk
without the normal public lookup, but is hidden/internal and was not called.

## Multi-instance

ActivityThread package/resource caches are keyed primarily by package identity
with user and code/resource dimensions. Same-package instances in one process
can therefore collide on LoadedApk, class loader, resources, and Application
cache state. Separate instance directories do not solve this. Same-package
multi-instance support is `CRITICAL / UNKNOWN`.

## Sources

All sources are `PRIMARY / AOSP`, accessed September 23, 2026:

1. https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/
2. https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/Application.java
3. https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/Instrumentation.java
4. https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/ContextImpl.java
5. https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/LoadedApk.java
6. https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/ActivityThread.java
7. https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/
8. https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/
9. https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/
10. https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/app/ActivityThread.java

No third-party source was copied.

Task-14 decision: hidden observation remains unapproved. The public-only B0-P
experiment is allowed; field reads, reflection into framework classes, and
`getPackageInfoNoCheck` remain deferred.

## Task-15 audit and public C1 results

Hidden observation request = NOT NEEDED NOW. B0 inflater and configuration
context observations were C0 delegation gaps, not LoadedApk field evidence.
C1 publicly repairs those gaps and binds applicationContext to the Guest
Application. API31/API36 matrices and minimal onCreate passed. This does not
confirm any hidden field value or remove broader factory/component limits.
No unresolved measured behavior currently requires framework field reads.
