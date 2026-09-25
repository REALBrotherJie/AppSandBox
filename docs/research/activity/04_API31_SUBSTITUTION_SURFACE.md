# API31 Activity Substitution Surface

状态：PREFLIGHT RESEARCH，Android 12 / API31，2026-09-25。来源锚点为 `SOURCES.md` 的 A01-A08、A14-A17、A26-A28。

## Launch data flow

```text
LaunchActivityItem.execute(ClientTransactionHandler, ActivityClientRecord, PendingTransactionActions)
 -> ActivityThread.handleLaunchActivity(ActivityClientRecord, PendingTransactionActions, Intent)
 -> ActivityThread.performLaunchActivity(ActivityClientRecord, Intent)
 -> LoadedApk selected from ActivityInfo.applicationInfo
 -> ContextImpl.createActivityContext(ActivityThread, LoadedApk, ActivityInfo, token, displayId, overrideConfig)
 -> Instrumentation.newActivity(ClassLoader, className, Intent)
 -> Activity.attach(Context, ActivityThread, Instrumentation, token,
                    Application, Intent, ActivityInfo, configuration, ...)
 -> Instrumentation.callActivityOnCreate
```

这些名称和关系是源码锚点，不是应用兼容契约。未来 API31 adapter 必须在执行前重新发现完整 hidden 签名。

## Surface inventory

| Symbol/state | Role and source relationship | Classification | First probe |
|---|---|---|---|
| `ActivityThread.handleLaunchActivity` | receives the launch record and prepares window/config state before `performLaunchActivity` | INTERNAL, VERSION-SENSITIVE, OEM-SENSITIVE | observe only |
| `ActivityThread.performLaunchActivity` | resolves package/context, instantiates Activity, calls `attach`, then create lifecycle | INTERNAL, VERSION-SENSITIVE | observe entry/order only |
| `ActivityThread.ActivityClientRecord` | carries token, Intent, ActivityInfo, compat/config, saved state and resulting Activity reference | HIDDEN/NON-SDK, VERSION-SENSITIVE | type/field-name inventory only |
| `ClientTransaction` | transports callbacks/lifecycle requests for a client token | HIDDEN/NON-SDK | transaction type/order only |
| `LaunchActivityItem.execute` | hands launch payload to `handleLaunchActivity` | HIDDEN/NON-SDK, VERSION-SENSITIVE | observe only |
| `Instrumentation.newActivity(ClassLoader,String,Intent)` | public construction API called with selected LoadedApk class loader and ActivityInfo class name | PUBLIC | call-site observation only |
| `AppComponentFactory.instantiateActivity` | may participate beneath Instrumentation construction | PUBLIC factory, framework-controlled invocation | NOT REQUIRED FOR FIRST PROBE |
| `Activity.attach` | binds Context, thread, instrumentation, token, Application, Intent, ActivityInfo, configuration and Window state | INTERNAL, VERSION-SENSITIVE | prohibited |
| `LoadedApk` | obtained from Host `ActivityInfo.applicationInfo`; owns Host class loader/resources/Application path | INTERNAL | identity observation only |
| `ContextImpl.createActivityContext` | creates token/display/config-bound Activity context from LoadedApk and ActivityInfo | INTERNAL, VERSION-SENSITIVE | call-order observation only |
| Host `ActivityInfo` | supplies Host class/package/application/theme/config behavior | PUBLIC data type, system-origin record | observe; never mutate in P0 |
| `Intent.component` | remains Host Stub component in the approved transaction | PUBLIC data, system-approved | observe |
| token/config/display | system carrier token and merged/override display configuration feed Context/attach | INTERNAL relationship, OEM-SENSITIVE | presence/order only |
| Window | created and bound during attach from Host carrier state | INTERNAL consequence | NOT REQUIRED FOR FIRST PROBE |

## Construction, attach and failure

`Instrumentation.newActivity` is the narrowest class-selection call site, but observing it does not authorize replacing Instrumentation. A Guest class requires a verified Guest class loader; the normal call uses Host LoadedApk and Host `ActivityInfo.name`. Changing only the name can fail class loading. Changing only the loader leaves Host metadata and Guest code inconsistent.

Before `Activity.attach`, API-shape mismatch, access denial, missing mapping, verification failure, missing class and non-Activity type can fail closed while preserving an untouched Host launch. After Activity object selection or client-record mutation, rollback becomes less reliable. Once attach begins, Context, token, Window, ActivityInfo, Application and configuration become coupled; P0/P1 must not cross that boundary.

Framework construction exceptions propagate through the launch path and can fail the launch or process. P0 therefore records access errors without throwing into Host launch.

## API31 P0 gate

P0 may report only type names, method ordering, payload shape, access outcome and Host fallback evidence. It must not inspect token internals, mutate `ActivityClientRecord`, replace class name/class loader, construct Guest Activity, call attach or alter lifecycle.
