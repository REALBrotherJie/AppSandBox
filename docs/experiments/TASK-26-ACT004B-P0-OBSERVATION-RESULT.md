# TASK-26 ACT-004B-P0 Observation-Only Internal Surface Probe

日期：2026-09-26

## Question

在不调用 hidden/internal 方法、不修改 transaction/client record、不替换 dispatch 且不构造 Guest Activity 的条件下，API31/API36 debug 进程能否观察 Activity launch internal surface 的真实运行时实例和顺序？

## Scope and forbidden actions

本轮只使用 `Class.forName`、声明元数据枚举、现有 Host Stub 外部生命周期、logcat 和 dumpsys。没有调用 `Activity.attach`、Guest lifecycle、Instrumentation replacement、Hook、Binder、ServiceManager、JVMTI、native instrumentation、内部方法或 `setAccessible`，没有修改 transaction、ActivityClientRecord、ActivityInfo、Intent、classloader、Context、Window 或 token。

## Static versus runtime evidence

API31/API36 上目标类全部可发现，声明方法、字段和内部类枚举成功。目标包括 ActivityThread、ActivityClientRecord、ClientTransaction、LaunchActivityItem、TransactionExecutor、ClientTransactionHandler、LoadedApk、ContextImpl、Instrumentation、ActivityInfo、Intent、Window 和 IBinder。静态类发现不等于真实 transaction 观察。

两端均为：

```text
ACT-004B-P0 = STATIC_SURFACE_ONLY
runtime instance observed = NO
real transaction/client record observed = NO
```

公开 ActivityLifecycleCallbacks、Host Stub logcat 和 dumpsys 只能证明外部 Host 生命周期与 system record，不能证明内部 transaction instance。

## Host external lifecycle evidence

| Device | API | Stub taskId | lifecycle | window/token | fallback |
|---|---:|---:|---|---|---|
| `7b670025` | 31 | 60 | onCreate -> onStart -> onResume -> window focus | PhoneWindow; both tokens non-null | preserved |
| `emulator-5554` | 36 | 16 | onCreate -> onStart -> onResume -> window focus | PhoneWindow; both tokens non-null | preserved |

两端 Host component 均为 `com.example.appsandbox/.experiments.act003.Act003StubActivity`，Guest package `pm path` 为空，Host survives=true。没有 Guest object 或 Guest lifecycle。

## Access failures and abort gates

声明元数据访问没有失败，但安全边界内不存在真实 transaction/client-record instance 的非侵入式观察入口。进入内部实例观察将需要内部调用、dispatch hook、字段访问或其他被禁止机制，因此没有继续。结果不是 `FAILED_CLOSED`，因为静态 surface 和 Host fallback 均完整；也不是 `INTERNAL_SURFACE_OBSERVED`。

## L0-L5 and authorization

```text
L0 = CONFIRMED
L2 = CONFIRMED
L3 = NOT CONFIRMED
L4 = NOT TESTED
L5 = NOT AVAILABLE
P1 pre-attach class selection = NOT AUTHORIZED
P2 attach/substitution = NOT AUTHORIZED
```

## Build/test

```text
:app:testDebugUnitTest = PASS
:app:assembleDebug = PASS
:app:assembleRelease = PASS
:test-guests:GuestTestApp:assembleDebug = PASS
git diff --check = PASS
```

Release 不包含 P0 runner、task26 debug entry 或内部访问逻辑；没有新增依赖。

## Modified files

```text
app/src/debug/java/com/example/appsandbox/experiments/act004b/Act004bP0Runner.kt
app/src/debug/java/com/example/appsandbox/experiments/v1/ExperimentActivity.kt
scripts/task26-run.ps1
docs/research/activity/06_API31_P0_OBSERVATION.md
docs/research/activity/07_API36_P0_OBSERVATION.md
docs/experiments/TASK-26-ACT004B-P0-OBSERVATION-RESULT.md
docs/experiments/evidence/task26/<serial>/
docs/design/ACTIVITY_CLIENT_ADAPTER_PLAN.md
```

## Conclusion

```text
ACT-004B-P0 = STATIC_SURFACE_ONLY
API31 runtime instance observed = NO
API36 runtime instance observed = NO
Host fallback preserved = YES
Transaction mutation = NO
Internal invocation = NO
Guest Activity constructed = NO
Guest Activity attached = NO
Guest lifecycle executed = NO
Guest system-installed = false
Binder = NO
Hook = NO
hidden API bypass = NO
P1 = NOT AUTHORIZED
P2 = NOT AUTHORIZED
```

P0 没有授权 P1 或 P2。静态 surface 不能直接升级为 class selection 或 attach 实验。
