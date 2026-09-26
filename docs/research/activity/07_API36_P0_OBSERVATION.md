# API36 P0 Observation Result

日期：2026-09-26。设备：`emulator-5554`，API36，x86_64，page size 4096。

## Static surface

`ActivityThread`、`ActivityThread$ActivityClientRecord`、`ClientTransaction`、`LaunchActivityItem`、`TransactionExecutor`、`ClientTransactionHandler`、`LoadedApk`、`ContextImpl`、`Instrumentation`、`ActivityInfo`、`Intent`、`Window` 和 `IBinder` 均 `classFound=true`。声明 methods/fields/classes 均枚举成功，结果在 task26 evidence 的 `surface-inventory.txt`。

没有 `setAccessible`、实例字段读取、内部方法调用、transaction 修改或 lifecycle 修改。

## Runtime observation

```text
runtimeInstanceObserved=NO
internalObservationOutcome=STATIC_SURFACE_ONLY
p0.phase=STATIC_SURFACE_ONLY
```

没有观察真实 transaction/client record 实例。Host Stub 的外部生命周期和 dumpsys 证据单独保存，不能替代内部 runtime observation。

## Host external evidence

Host Stub component 为 `com.example.appsandbox/.experiments.act003.Act003StubActivity`，taskId 为 16。Host 记录了 onCreate、onStart、onResume、window focus；PhoneWindow 存在，decor window token 和 application window token 均非空，Guest Activity 未构造，Guest lifecycle 未执行，Host 存活。

## Outcome

声明元数据访问成功，但 API36 没有在本实验允许边界内提供安全的真实 transaction observation 入口。因此结果为 `STATIC_SURFACE_ONLY`，不是 `INTERNAL_SURFACE_OBSERVED`，也不是 L3 证据。
