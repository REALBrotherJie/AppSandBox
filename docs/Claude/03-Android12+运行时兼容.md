# 03 Android 12+ 运行时兼容（阶段 2、3、4、13、16）

沙箱要在现代系统上跑真实第三方 app，最大的一类工作是**跟上系统服务接口的版本漂移**，
以及处理 **Android 12 的身份校验**。这些多数是通用平台事实，与具体实现无关。

## 通用原则：按类型定位参数，不要按固定下标

系统服务的 AIDL 方法在各版本**中间插入了新参数**（callingPackage、featureId、AttributionSource…）。
旧沙箱代理按固定下标取参数，在新系统上必然取错。**所有 hook 代理都应改为"按参数类型查找"**
（找 `Intent` / `IntentFilter` / `IIntentReceiver` / 权限数组 / `AttributionSource` 等），
或"从参数尾部倒数取"（尾部参数相对稳定）。

## Android 12：AttributionSource uid 校验（最容易击穿一切）

Android 12 (S) 给**每次 ContentResolver 调用**附带一个 `AttributionSource`，
系统强制其 uid == **真实 binder 调用方 uid**。沙箱里 guest 上下文用的是**伪造的虚拟 uid**，
而内核 uid 是宿主 uid，于是任何"未被沙箱包装"的 provider 调用都抛：

```
SecurityException: Calling uid: <host> doesn't match source uid: <vuid>
```

踩到的三处未包装路径：

1. **WebView 初始化**：`WebViewChromiumFactoryProvider` 查一个 chromium ContentProvider，未被包装 → 整个 app FATAL（阶段 13）。
2. **Tinker（腾讯热修复）**：WPS 的 `KApplication` 在 `attachBaseContext` 期就访问 provider，比一般时机更早（阶段 16）。
3. 一般的 `ContentResolver.call/query` 到未包装 provider。

**解决**：把 guest 上下文的 `mAttributionSource` 重建为**真实宿主身份**（realUid + hostPkg）。关键两点：

- 取真实 uid 不能用 `Process.myUid()`，**连 `Os.getuid()` 都被沙箱伪造过**；
  必须用启动早期（任何伪造 hook 安装前）捕获的真实宿主 uid。
- 修复时机要**足够早**：既要在 `makeApplication` 之后修（覆盖后续 Activity 等上下文），
  也要在 `Instrumentation.newApplication` 里、Application 自己的 `attachBaseContext` **之前**修
  （否则 Tinker 这类在创建期就访问 provider 的 app 赶不上）。
- 对沙箱自己包装的 provider，同样按宿主身份处理，保持一致。

## 系统服务改名/加参一览（实测）

| 能力 | 旧方法 | 新方法（触发版本） | 症状 |
|---|---|---|---|
| 绑定服务 | `bindService` | `bindIsolatedService`(11) → `bindServiceInstance`(12) | 绑定从不生效 |
| 广播发送 | `broadcastIntent` | `broadcastIntentWithFeature`(11+) | 广播收不到 |
| 动态注册 | `registerReceiver` | `registerReceiverWithFeature`(11+) | 广播收不到 |
| PendingIntent | `getIntentSender` | `getIntentSenderWithFeature`(11+) | `not allowed to send as package`，PendingIntent/AlarmManager 全废 |
| 通知渠道 | 直传包名 | 需替换为宿主包名 | `Unknown package` |
| Toast | — | `enqueueTextToast`/`finishToken`(11+) 需替换调用方包名 | — |
| ContentProvider.call | 固定下标 | 前置参数漂移（callingPkg→authority→AttributionSource） | 取错 (method,arg,extras) → 改为从尾部取 |
| getContentProvider | name 在下标 1 | Android 8+ 插入 callingPackage，name 移到下标 2 | 取错 |

**通用小工具**：`ReplaceAppPkgMethodProxy`——把所有"等于当前 app 包名"的参数替换为宿主包名，
批量注册给通知渠道等一堆方法，避免 `Unknown package`。

## JobScheduler / WorkManager

两个独立 bug（阶段 4）+ 一个类型 bug（阶段 7）：

1. **桩 JobService 从不创建**：绑定目标 JobService 用 `flags=0`，而沙箱只在带 `BIND_AUTO_CREATE` 时才创建服务。
2. **Job id 撞号**：`mGlobalJobId` 初始化为"已有 id 最大值"而非"最大值+1"，下一个新任务必与已有任务撞号，桩绑错 app。
   修为最大值+1，并在加载存量数据时检测并重分配重复 id。
3. **`getAllPendingJobs` 类型不符**：框架 `JobSchedulerImpl` 期望 binder 返回 `ParceledListSlice` 再 `.getList()`，
   直接返回 `List<JobInfo>` 会 ClassCastException，导致 WorkManager 初始化失败。按方法返回类型用 `ParceledListSlice` 包装。
4. **桩健壮性**：绑定后 5 秒内客户端未连接就应答放弃（框架只给约 8 秒），任务结束释放绑定，消除 `ServiceConnectionLeaked`。

## ContentObserver（阶段 15）

app 对**自有 Provider** 注册 `ContentObserver` 时，URI authority 被真实 ContentService 解析到
"设备上另一份同名 app"的 provider（uid 不符 + 非 exported）→ `SecurityException` → onCreate 崩。

- **解决**：代理 `registerContentObserver`/`unregisterContentObserver`/`notifyChange`，
  吞掉这类 `SecurityException` 降级为 no-op（自有 provider 的变更通知不触发，但不崩）；exported/系统 provider 不受影响。

## 错误可见性（阶段 4）

`makeApplication` 失败时 `mInitialApplication` 为 null，旧代码取它的类名会抛 NPE **掩盖真实原因**。
改为回退到清单里的 className，让真正的异常暴露出来。**沙箱开发要保证失败路径不掩盖根因。**

## 给 AppSandBox 的启示

- 若将来做系统服务代理，从第一天就**按类型/尾部定位参数**，并建一张"方法名随版本改名"的映射表。
- Android 12 的 AttributionSource 是硬约束：任何跨进程 provider 调用都要携带与真实 binder uid 一致的身份。
- 真实 uid 要在启动最早期捕获并缓存，因为进程内的 `Process.myUid()`/`Os.getuid()` 都可能被沙箱改过。
