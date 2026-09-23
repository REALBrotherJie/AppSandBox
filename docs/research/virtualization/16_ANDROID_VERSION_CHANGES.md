# 16 Android Version Changes (12 → 16)

状态标签：`CPS`=CONFIRMED BY PRIMARY SOURCE，`MIR`=MULTIPLE INDEPENDENT REPORTS，`SSS`=SINGLE SECONDARY SOURCE，`SPEC`=SPECULATIVE，`NE`=NEEDS EXPERIMENT。来源编号见 `00_SOURCES.md`。

范围：只保留和 AppSandbox 相关的变化。Host 当前 `targetSdk = 36`，所以所有「targeting Android X」的规则对 Host 都生效。当前实验设备是 Android 12 / API 31，**下表中 13–16 的影响都还没有在真机上验证**。

## 1. 需要立刻处理的问题

### 1.1 Android 14：动态加载的文件必须是只读的
- 规则：targetSdk 34+ 的应用，所有动态加载的文件（DEX/JAR/APK）必须在写入完成后立即设为只读，否则加载失败。CPS `[S18]`；运行时存在对应的强制开关 `DexFile.isReadOnlyJavaDclEnforced()`。CPS `[S60]`
- 现状：`GuestStore` 写入 `base.apk` 后**没有**调用 `setReadOnly()`（本地代码检查，2026-09-23）。EXP-001 是在 Android 12 上验证的，还没有触发这条规则。
- 影响：在 API 34+ 设备上，EXP-001 的 `DexClassLoader` 路径很可能直接失败。状态：规则为 CPS，失败本身为 NE。
- 建议：新增实验 V-1（见第 4 节）。这条规则与 ADR-0003「不可变版本」的方向一致，修复成本很低。

### 1.2 Android 12+：AttributionSource 校验调用方 UID
- 真实案例：在虚拟化环境里，把 Guest 的虚拟 UID 写进跨进程 Provider 请求，会被系统以 `Calling uid … doesn't match source uid …` 拒绝。已有 Android 13 `[S37]` 和 Android 16 `[S38]` 两份独立报告，另有 VirtualXposed 的同类报错 `[S39]`。MIR
- 对 AppSandbox：ADR-0002「系统边界上保留 Host 身份，不伪造 UID」与这些案例总结出的修复方向**完全一致**。这是一次外部验证，应在 ADR-0002 中引用这些证据。

## 2. 分版本变化表

| 版本 | 变化 | 对 AppSandbox 的影响 | 来源 | 状态 |
|---|---|---|---|---|
| 12 | PendingIntent 必须显式声明可变或不可变；带 intent-filter 的组件必须声明 `exported` | Guest 的 PendingIntent 和 manifest 语义需要翻译 | `[S64]` | SSS（摘要） |
| 12 | AttributionSource 与调用方 UID 一致性校验 | 见 1.2 | `[S36][S37][S38]` | MIR |
| 14 | 前台服务必须声明类型 | Guest Service 若要以前台方式运行，Host 需要预先声明对应类型的 stub | `[S18]` | CPS |
| 14 | 隐式 intent 只投递给 exported 组件 | Guest 内部用隐式 intent 启动自己的组件时，需要 Host 路由 | `[S18]` | CPS |
| 14 | 动态注册的 receiver 必须指定 `RECEIVER_EXPORTED` / `RECEIVER_NOT_EXPORTED` | Guest 调用旧 API 注册 receiver 时需要补参数 | `[S18]` | CPS |
| 14 | 动态加载的代码必须只读 | 见 1.1 | `[S18]` | CPS |
| 14 | 后台启动 Activity 收紧（PendingIntent 发送方、bindService 需要显式授予） | Guest 从后台拉起界面的路径受限 | `[S18]` | CPS |
| 14 | JobScheduler 回调超时会触发 ANR；使用网络约束需要 `ACCESS_NETWORK_STATE` | 代理 Guest 的 Job 时要按 Host 身份满足这些条件 | `[S18]` | CPS |
| 15 | `dataSync` / `mediaProcessing` 前台服务每 24 小时限 6 小时；`BOOT_COMPLETED` 不能启动部分类型的前台服务 | Guest 的长时间后台任务会被系统截断 | `[S19]` | CPS |
| 15 | PendingIntent 创建方默认阻止后台启动 Activity | 同上，进一步收紧 | `[S19]` | CPS |
| 15 | 默认强制 edge-to-edge；`Configuration.screenWidthDp/HeightDp` 不再扣除系统栏 | Guest 界面的布局和它读到的配置会与 Android 12 不同 | `[S19]` | CPS |
| 15 | 设备开始支持 16 KB 内存页 | 见下一行 | `[S26]` | CPS |
| 15/16 | 16 KB 页：Google Play 自 2025-11-01 起要求面向 Android 15+ 的应用支持；Android 16 对只支持 4 KB 的应用启用兼容模式并提示用户 | Host 自己的 .so 必须 16 KB 对齐；**Guest 自带的 .so 无法重新编译**，在 16 KB 设备上的行为需要实验 | `[S26][S21]` | CPS；Guest 部分 NE |
| 16 | 非 SDK 接口列表继续更新；ART 内部结构变化，依赖内部结构的应用可能失效 | 进一步证明项目「不依赖 hidden API」的策略是对的 | `[S17][S21]` | CPS |
| 16 | intent 重定向默认加固 | Host 作为中转，把 Guest 的 intent 转发给系统或其他组件时可能被拦截 | `[S21]` | CPS；影响 NE |
| 16 | 有序广播的优先级只在同一进程内有效 | 依赖跨进程广播优先级的 Guest 行为会改变 | `[S21]` | CPS |
| 16 | JobScheduler 配额按待机分组、前台状态等调整 | Guest 的 Job 共用 Host 配额，互相挤占 | `[S21]` | CPS；共用配额为 SPEC |
| 16 | 大屏（≥600dp）上忽略 `screenOrientation` / `resizableActivity` 等属性 | Guest 声明的方向锁在平板上失效 | `[S20]` | CPS |
| 16 | 强制预测性返回，`onBackPressed()` 不再被调用（除非 opt-out） | 依赖 `onBackPressed` 的 Guest 行为会因 Host stub 的设置而改变 | `[S20]` | CPS |
| 16 | 访问本地网络需要权限（逐步推行） | 权限挂在 Host 上，Guest 需要经过 Host 的权限代理 | `[S20]` | CPS |

## 3. 回答：「Android 16 相比 Android 12，我们当前的设计最可能在哪里失效？」Top 10

1. **动态加载只读**（1.1）：EXP-001 的前提在 API 34+ 上可能直接失败。影响最大，修复也最简单。
2. **系统服务身份**：凡是需要 AttributionSource 或包名、UID 一致性的调用，只能以 Host 身份进行（1.2）。
3. **前台服务类型与时长**：Host manifest 必须预先声明 Guest 可能用到的每一种前台服务类型，而且还要受时长限制。
4. **后台启动 Activity**：Guest 的通知点击、闹钟、推送拉起界面等路径，在 14/15 上连续收紧。
5. **16 KB 页与 Guest native 库**：Guest 的 .so 无法重新编译。
6. **intent 重定向加固**：Host 作为 intent 中转层时直接受影响。
7. **大屏方向与 edge-to-edge**：Guest 的界面假设与 Host stub 的实际窗口配置不一致。
8. **广播语义**：隐式广播、exported 标志、优先级范围都在变化。
9. **JobScheduler 配额**：所有 Guest 共用 Host 的配额。
10. **非 SDK 接口与 ART 内部变化**：任何依赖框架内部结构的方案，维护成本会逐版本上升。

## 4. 建议实验（不执行）

| 编号 | 内容 | 通过条件 |
|---|---|---|
| V-1 | 在 API 34+ 设备上重跑 EXP-001：先不设只读，再设只读 | 确认 Android 14 规则对私有目录下的 base.apk 是否生效，以及设为只读后能否正常加载 |
| V-2 | 在 16 KB 页设备或模拟器上加载带 4 KB 对齐 .so 的 Guest | 记录 `System.loadLibrary` 的实际结果 |
| V-3 | 在 API 35/36 上重跑 EXP-002 Option B | 与 API 31 的结果一致 |
