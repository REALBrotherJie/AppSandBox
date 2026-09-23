# EXP-003 Minimal Guest Application Specification

This is a specification only. No Guest Application implementation is added in
this design round.

## Class

```text
com.example.appsandbox.testguest.runtime.Exp003GuestApplication
```

It extends `android.app.Application` and is loaded only by the Guest
ClassLoader.

## Minimal onCreate

The first implementation may read only:

```text
getPackageName()
getClassLoader()
getResources()
getAssets()
getFilesDir()
getCacheDir()
getApplicationInfo()
```

It returns a JSON report containing strings, paths, class names, package
metadata, main-thread status, and the marker:

```text
EXP003_APPLICATION_2f7c9a41-6d0e-4b83-a512-9e6f3c7d8014
```

The marker must be defined in the Guest APK and absent from Host resources.

## Prohibited in the first Guest Application

No PackageManager query, ContentResolver, Provider, Service, Receiver,
Activity, network, WebView, native library, third-party SDK, WorkManager,
Firebase, database, notification, or system-service call.

## Result Transport

Use a JSON string or primitive/string map across the ClassLoader boundary.
Do not cast Guest custom objects to Host interfaces because class identity is
loader-specific.

