# Android 12 Activity Launch Chain

状态：RESEARCHED DESIGN，API 31，2026-09-24。

```text
Context.startActivity
 -> Instrumentation.execStartActivity
 -> ActivityTaskManager client/Binder
 -> ATMS in system_server
 -> ActivityStarter
 -> ActivityRecord / Task / activity token
 -> ClientTransaction / LaunchActivityItem
 -> ActivityThread / TransactionExecutor
 -> performLaunchActivity
 -> ContextImpl.createActivityContext
 -> Instrumentation.newActivity / AppComponentFactory.instantiateActivity
 -> Activity.attach
 -> Window setup
 -> onCreate(savedState)
```

| Stage | Side | Input/output | System view |
|---|---|---|---|
| `Context.startActivity` | client | Intent and caller context | delegates to instrumentation |
| `Instrumentation.execStartActivity` | client | Intent, `callingPackage`, `callingFeatureId` | caller metadata is sent; Binder UID remains authoritative |
| `IActivityTaskManager.startActivity` | boundary | start request | service receives UID and validates package relationship |
| ATMS/ActivityStarter | system_server | Intent, caller, resolved info, flags | PMS resolution, exported/permission checks, task and launch-mode policy |
| ActivityRecord/Task | system_server | resolved component and state | owns logical Activity, task, token and lifecycle policy |
| ClientTransaction | boundary | transaction and activity client record | delivers system-approved component data |
| LaunchActivityItem | client | activity info, token, intent, saved state | instructs client construction |
| `performLaunchActivity` | client | record | selects LoadedApk/class loader/instrumentation |
| `createActivityContext` | client | token, display, override config, ActivityInfo, LoadedApk | makes Activity-specific ContextImpl |
| `newActivity` | client | class loader and class name | creates object, not system identity |
| `Activity.attach` | client framework | Context, thread, instrumentation, token, app, intent, info, config | binds object to approved record/window |
| `onCreate` | client | saved state and intent | runs after attach/window setup |

The token is created and validated by system_server. `ActivityInfo` comes from installed package state. A direct Intent for an uninstalled Guest has no normal Guest ActivityInfo and is expected to fail resolution. `callingPackage` is not a substitute for Binder UID or package installation.
