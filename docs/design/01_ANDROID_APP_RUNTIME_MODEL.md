# Android App Runtime Model

Status vocabulary: `CONFIRMED` means supported by public Android API/AOSP documentation; `PROPOSED` means AppSandbox design; `RESEARCH NEEDED` means version/device behavior must be measured; `BLOCKED` means the design cannot provide an equivalent without platform privileges.

## 1. Installation creates more than an APK path

An APK is an archive containing code, resources, manifest metadata, and optional native libraries. Installation turns that archive into system-owned package state. The important result is not merely copying bytes:

1. Package parsing validates the manifest and creates package/component/permission metadata.
2. PackageManagerService persists package settings and makes the package visible to system services.
3. PackageInstaller coordinates the session and commit; installd performs privileged filesystem and package-data work.
4. The package receives an Android app identity for a user: appId/UID, user data directories, and SELinux application domain.
5. The package's code/resource/native paths become known to the runtime.
6. Permissions, app-op state, enabled state, installer/source state, signing information, and component visibility become system state.
7. Optimized code may be produced or selected by ART/dex2oat. Exact timing and artifacts vary by release and device.

Relevant AOSP areas include `frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java`, package parsing under `frameworks/base/core/java/android/content/pm/` and `frameworks/base/services/core/java/com/android/server/pm/`, `PackageInstallerService`, `PackageInstallerSession`, and `system/core/installd`. Exact class names and call paths must be checked per Android branch.

`CONFIRMED`: an uninstalled APK has no PMS package record, no system-assigned guest UID, no system-managed component registration, and no normal app data context. `RESEARCH NEEDED`: exact dex optimization and native-library extraction behavior for each target release.

## 2. Process startup

The normal conceptual path is:

```text
Launcher or caller
  -> ActivityTaskManagerService
  -> ActivityManagerService / ProcessList
  -> Zygote socket request
  -> forked app process
  -> ActivityThread.main()
  -> attachApplication()
  -> bindApplication()
  -> LoadedApk / ContextImpl
  -> makeApplication()
  -> Application.onCreate()
  -> component transaction
```

`ActivityThread` is the client-side process coordinator. `LoadedApk` packages application metadata, class loading, resources, and data paths. `Instrumentation` and `AppComponentFactory` participate in object creation. The system side owns process/task decisions; the app process owns component object creation after a transaction arrives.

`CONFIRMED`: Zygote supplies process creation and the platform binds an installed package identity to the process. `PROPOSED`: AppSandbox must treat process creation, package identity, and component dispatch as separate contracts rather than pretending an APK is installed.

## 3. Components

| Component | Registration | Scheduling | Instance creation | Lifecycle owner |
|---|---|---|---|---|
| Activity | PMS manifest metadata | ATMS/AMS | ActivityThread transaction | ATMS plus client ActivityThread |
| Service | PMS metadata; dynamic bindings in AMS | AMS `ActiveServices` | ActivityThread service transaction | AMS plus client Service |
| Receiver | PMS static metadata or dynamic registration | AMS broadcast queues | ActivityThread receiver transaction | AMS for delivery, receiver callback |
| Provider | PMS metadata and authority map | AMS/provider manager | ActivityThread provider installation | AMS/provider manager plus client |

The system resolves exported state, permissions, process name, user, visibility, and component enabled state before delivery. The app receives a component-specific `Context`; it is not sufficient to instantiate a Java object in isolation.

## 4. System-service path

The common model is:

```text
Framework Java API
  -> manager facade (for example PackageManager or NotificationManager)
  -> generated/manual Binder proxy
  -> Binder driver
  -> system_server Binder stub
  -> service implementation
  -> identity and permission checks
```

Examples include `IPackageManager`, `IActivityManager`, `IActivityTaskManager`, `INotificationManager`, `IClipboard`, `IAccountManager`, and location/connectivity Binder interfaces. Some managers cache local state or use a system-server-side helper; not every call is a single direct transaction.

`CONFIRMED`: the host process normally presents its real UID/package to system services. `BLOCKED`: an ordinary application cannot create a second kernel UID or SELinux domain for an uninstalled guest. A compatibility layer can alter calls made by guest code, but it cannot claim to be an OS-level sandbox.

## 5. Missing-installation results

An uninstalled guest lacks system registration, UID/domain, data paths, component resolution, permission grants, package-manager visibility, and trusted lifecycle transactions. AppSandbox can reproduce selected logical behavior in user space; it cannot reproduce kernel identity, system-server ownership, or privileged installation effects without platform support.

## References

- AOSP: `frameworks/base/core/java/android/app/ActivityThread.java`
- AOSP: `frameworks/base/core/java/android/app/LoadedApk.java`
- AOSP: `frameworks/base/services/core/java/com/android/server/wm/ActivityTaskManagerService.java`
- AOSP: `frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java`
- AOSP: `frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java`
- AOSP: `system/core/rootdir/init.zygote*.rc`, `frameworks/base/core/java/com/android/internal/os/ZygoteInit.java`
- Android Developers: App fundamentals and manifest documentation
