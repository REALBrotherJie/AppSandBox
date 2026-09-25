# TASK-24 ACT-004A Negative Public-Only Logical Restore Ceiling

日期：2026-09-25

## Question

在不使用 hidden/internal API、Hook、Binder、`Activity.attach` 或 Guest 生命周期调用的条件下，Host Stub 能否恢复 Guest logical record，并证明独立构造的 Guest Activity Java object 仍不是 system-managed Activity？

## Hypothesis

公开 API 足以验证 production Guest artifact、建立 primitive logical mapping、构造独立 Guest Activity object，并将 Host Stub 启动关联到同一 `launchId`/`instanceId`。公开 API 不足以让 Guest object 进入 framework Activity lifecycle，也不能给它 Host token/window/task identity。

## Scope

本实验仅新增 debug runner、复用 ACT-003 Stub、增加采证脚本并在 API31/API36 真机环境运行。没有修改 `app/src/main/`、GuestStore、GuestPackageReader 或 Guest APK 源码，没有实现 substitution、attach 或 lifecycle translation。

## Host/Guest/bridge state

| Layer | Observed state |
|---|---|
| Host system state | `Act003StubActivity`、Host taskId、PhoneWindow、DecorView、非空 window token/application window token、Host lifecycle |
| Guest logical state | guestPackage、guestComponent、revisionId、launchId、instanceId、originalAction |
| Guest object state | Guest DexClassLoader 创建的两个独立 Java object；明确 `UNATTACHED` |
| Client bridge state | `RECEIVED -> MAPPED -> CLASS_LOADED -> OBJECT_CREATED -> UNATTACHED` |

Host task/window/token 始终标记为 Host carrier。Guest token/window/task 没有被宣称或推导。

## Production artifact verification

两端均通过 `GuestStore` production revision 和 `GuestArtifactVerifier`：

```text
verification=VALID
artifact.canWrite=false
record SHA == actual SHA == 6b3c0d1758be4f01156a7e74620614f09add22412f088ebfaa6a3fe209a7ade3
fileSize=49300
```

ClassLoader 只在上述验证完成后创建。

## Logical mapping phases

API31 与 API36 均记录：

```text
RECEIVED
MAPPED
CLASS_LOADED
OBJECT_CREATED
UNATTACHED
```

Intent 只传递 String primitive extras。没有传递 Guest Parcelable、Bundle、ActivityInfo、Binder token 或 Guest ClassLoader 对象。

## Host Stub evidence

| Observation | API31 | API36 |
|---|---|---|
| component | `com.example.appsandbox/.experiments.act003.Act003StubActivity` | same |
| taskId | 57 | 14 |
| window | `com.android.internal.policy.PhoneWindow` | same |
| lifecycle | onCreate, onStart, onResume, window focus | same |
| decor window token | non-null | non-null |
| application window token | non-null | non-null |
| logical mapping | UNATTACHED | UNATTACHED |

Stub content 仍是 Host 自有 TextView。Guest object 未进入 Host content hierarchy。

## Guest unattached evidence

两端均通过 public constructor 和 public `Instrumentation.newActivity` 创建 Guest object，两个对象彼此不同，Guest class loader 是独立 DexClassLoader 且不同于 Host class loader。

```text
getApplication=null
getWindow=null
getIntent=null
getPackageName=NullPointerException caused by null base Context
guest.attached=false
guest.lifecycleExecuted=false
guest.tokenWindowTask=NOT_CLAIMED
guest.inHostContentHierarchy=false
```

没有调用 Guest 生命周期、`setContentView`、创建 Guest Window 或执行 attach。

## Negative controls

- Direct Guest launch：API31/API36 均抛出 `ActivityNotFoundException`，结果为 `REJECTED`。
- Identity separation：Host runner class 与 Guest object class 不同；Guest class loader 与 Host class loader 不同。
- Framework lifecycle separation：证据中只有 Host Stub lifecycle，Guest lifecycle 为 false。
- Guest install state：两端 pre/post `pm path` 均为空。

## Invalid mapping

两端均执行缺失 `launchId` 的 fail-closed 变体：

```text
mappingResult=REJECTED
mapping.phase=REJECTED_BEFORE_CLASS_LOAD
guestObjectCreated=false
guest.attached=false
guest.lifecycleExecuted=false
hostSurvived=true
```

## API31

设备 `7b670025`，Xiaomi Mi 10，API31，arm64-v8a，page size 4096。Production revision `94030072-fc49-4300-876f-55e4c60f4aa4`。有效映射、Host Stub carrier、Guest unattached object、直接启动拒绝、无效映射拒绝和 Host 存活均有设备证据。

## API36

设备 `emulator-5554`，sdk_gphone64_x86_64，API36，x86_64，page size 4096。Production revision `60954901-80b3-497b-8ae6-c8946d00ed1a`。结果与 API31 一致。

## L0-L5

```text
L0 = CONFIRMED by ACT-001
L2 = Java object construction observed by ACT-002 and repeated here
L3 = NOT CONFIRMED
L4 = NOT TESTED
L5 = NOT AVAILABLE
```

## Scope compliance

未调用 `Activity.attach`，未调用 Guest lifecycle，未替换 Instrumentation，未拦截 ActivityThread/ClientTransaction/LaunchActivityItem/Handler/Binder，未使用 hidden API、内部反射、Hook、Xposed/LSPosed、native Binder 或 seccomp。没有新增 Stub 类型或 Stub pool，没有修改 production Activity runtime。

## Build/test

```text
:app:testDebugUnitTest = PASS
:app:assembleDebug = PASS
:app:assembleRelease = PASS
:test-guests:GuestTestApp:assembleDebug = PASS
git diff --check = PASS
```

Release source set 不引用 ACT-004A runner 或 task24 mode；ACT-004A 代码只存在于 `app/src/debug/`。

## Modified files

```text
app/src/debug/java/com/example/appsandbox/experiments/act004a/Act004aNegativeRunner.kt
app/src/debug/java/com/example/appsandbox/experiments/act003/Act003StubActivity.kt
app/src/debug/java/com/example/appsandbox/experiments/v1/ExperimentActivity.kt
scripts/task24-run.ps1
docs/experiments/evidence/task24/<serial>/
docs/experiments/TASK-24-ACT004A-NEGATIVE-RESULT.md
```

`docs/Codex/task-23.txt` 与 `docs/Codex/task-24.txt` 是未跟踪输入文件，不纳入提交。

## Conclusion

```text
ACT-004A-NEGATIVE = CONFIRMED
Logical mapping = YES
Guest object constructed = YES
Guest object attached = NO
Guest lifecycle executed = NO
Guest token/window/task = NOT CLAIMED
Host Stub carrier = CONFIRMED
Guest system-installed = false
Direct Guest launch = REJECTED
Host survived = true
Production Activity runtime implemented = NO
Binder/Hook/hidden API used = NO
Ready for positive preflight/design = YES
Ready for positive implementation = NO
```

本结论只确认 public-only negative ceiling，不授权 positive substitution 或 attach 实现。
