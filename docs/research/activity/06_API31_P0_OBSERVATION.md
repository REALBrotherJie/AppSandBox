# API31 P0 Observation Result

日期：2026-09-26。设备：`7b670025`，API31，arm64-v8a，page size 4096。

## Static surface

`ActivityThread`、`ActivityThread$ActivityClientRecord`、`ClientTransaction`、`LaunchActivityItem`、`TransactionExecutor`、`ClientTransactionHandler`、`LoadedApk`、`ContextImpl`、`Instrumentation`、`ActivityInfo`、`Intent`、`Window` 和 `IBinder` 均 `classFound=true`。每个类的声明 methods/fields/classes 均枚举成功，结果在 task26 evidence 的 `surface-inventory.txt`。

观察器只读取声明元数据；没有 `setAccessible`、实例字段读取、内部方法调用或写操作。

## Runtime observation

```text
runtimeInstanceObserved=NO
internalObservationOutcome=STATIC_SURFACE_ONLY
p0.phase=STATIC_SURFACE_ONLY
```

没有观察真实 `ClientTransaction`、`LaunchActivityItem`、`ActivityClientRecord` 或 `TransactionExecutor` 实例，也没有将 Host lifecycle 推断成内部 transaction 证据。

## Host external evidence

Host Stub component 为 `com.example.appsandbox/.experiments.act003.Act003StubActivity`，taskId 为 60。Host 记录了 onCreate、onStart、onResume、window focus；PhoneWindow 存在，decor window token 和 application window token 均非空，Guest Activity 未构造，Guest lifecycle 未执行，Host 存活。

## Outcome

声明元数据访问成功，但没有安全的非侵入式 runtime transaction observation API。因此 API31 结果为 `STATIC_SURFACE_ONLY`，Host fallback 保持正常。
