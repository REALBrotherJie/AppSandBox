# 26 Gaps in Current AppSandbox

对照对象：`APP_SANDBOX_MASTER_DESIGN.md`、`design/*`、ADR-0001~0006、EXP-001、EXP-002（含复验）、EXP-003 设计文档（截至 2026-09-23 提交 `30db956`，以及工作区里正在进行的 task-11 修改之前的版本）。

状态标签：`CPS`=CONFIRMED BY PRIMARY SOURCE，`MIR`=MULTIPLE INDEPENDENT REPORTS，`PC`=PROJECT CLAIM，`SSS`=SINGLE SECONDARY SOURCE，`SPEC`=SPECULATIVE，`NE`=NEEDS EXPERIMENT。来源编号见 `00_SOURCES.md`。

## 1. Already considered（外部资料印证了现有设计）

| 设计点 | 外部证据 | 状态 |
|---|---|---|
| ADR-0002：系统边界上保留 Host 身份，不伪造 UID | 多个虚拟化项目在 Android 12+ 因为把虚拟 UID 写进 AttributionSource 而崩溃，修复方向正是「系统边界上使用 Host 的真实身份」`[S37][S38][S39]` | MIR |
| 默认不用 hidden API（design/20） | Android 16 继续收紧非 SDK 接口，并明确警告 ART 内部结构变化 `[S16][S17][S21]` | CPS |
| Guest 资源必须与 Host 隔离（EXP-002） | Shadow 同样用 `getResourcesForApplication` 为插件建立独立资源 `[S12]` | PC |
| 不可变版本路径（ADR-0003） | 能规避 ResourcesManager 的缓存问题（08 第 3 节），也符合 Android 14 只读加载规则的方向（16 第 1.1 节） | SPEC → NE |
| Play 限制（design/22） | 不得从 Play 以外的来源下载可执行代码 `[S23]`；`QUERY_ALL_PACKAGES` 受严格限制 `[S24]` | CPS |

## 2. Missing（设计中没有覆盖）

| # | 缺口 | 为什么重要 | 建议去向 | 状态 |
|---|---|---|---|---|
| M-1 | **Android 14 动态加载必须只读**：`GuestStore` 没有把 `base.apk` 设为只读 | EXP-001 在 API 34+ 上可能直接失败 | 新实验 V-1（`16_ANDROID_VERSION_CHANGES.md`） | 规则 CPS `[S18]`，失败 NE |
| M-2 | **`Application.mLoadedApk` 会指向 Host**；Controlled Context 最底层必须是 ContextImpl | EXP-003B 的崩溃点和泄漏项 | `07_APPLICATION_LOADEDAPK.md`；补进 EXP-003 泄漏矩阵 | CPS `[S60]` |
| M-3 | **Split APK**：只导入 `base.apk` | 从商店获取的现代应用普遍是 split 包；Option B 需要填 `splitSourceDirs` | 待写 `23_SPLIT_APK_SUPPORT.md` | CPS（参数存在），NE |
| M-4 | **WebView**：资源需要 WebView 共享库；多进程需要各自的数据目录后缀 | Shadow 为此专门初始化 WebView `[S12]`；Android 9 起多进程共享 WebView 数据目录会直接崩溃 `[S35][S60]` | 待写 `21_WEBVIEW_COMPATIBILITY.md` | PC + MIR |
| M-5 | **Guest native 库与 16 KB 页** | Guest 的 .so 无法重新编译 `[S26][S21]` | V-2 实验 | CPS（规则），NE |
| M-6 | **共用 Host UID 带来的权限越权** | 同一个虚拟化环境里的 Guest 共享 Host 的全部权限，学术界已有系统分类，并提出了细粒度的权限分离方案 `[S46]` | design/17 权限模型 | SSS（只读了摘要） |
| M-7 | **滥用风险与安全审查** | 学术界记录了利用虚拟化分发恶意载荷 `[S42]`、以「插件」名义控制受害应用 `[S45]` 的情况；这会直接影响 Play 审核和用户信任 | design/21、design/22：只允许用户经 SAF 主动选择 APK，不做远程下发 | SSS |
| M-8 | **Work Profile 替代路线**没有进入设计比较 | 对「双开」类需求，它在身份、兼容性和合规上都更优 | `19_WORK_PROFILE_ALTERNATIVE.md` | CPS（机制）+ PC |

## 3. Underestimated（考虑过，但低估了）

| # | 项目 | 外部证据 | 状态 |
|---|---|---|---|
| U-1 | **逐版本维护成本** | VirtualApp 商业版公开的更新日志显示，2021–2026 年几乎每一两个月都有新的 Android 版本适配或厂商兼容修复，一直持续到 Android 17 beta `[S01]` | PC |
| U-2 | **系统服务翻译的规模** | 成熟项目的更新日志和架构说明都显示，系统服务层的适配是持续性工作，而且涉及的服务范围一直在扩大（输入法、存储统计、使用统计、状态栏、JobScheduler 等）`[S01][S08]` | PC |
| U-3 | **`ResourcesLoader` 的语义** | 它会修改传入的 AssetManager，不是沙箱工具 `[S60]` | CPS |
| U-4 | **兼容性声明的可信度** | 多个 fork 声称支持 Android 15/16/17，但都没有测试记录或 release 佐证 `[S04][S05][S07]` | 已核对 |

## 4. Possibly wrong（值得重新审查）

| # | 现有结论 | 疑点 | 处理 |
|---|---|---|---|
| W-1 | ADR-0006 把 Option A 列为 API 30+ baseline | Option A 会修改进程全局的系统 AssetManager `[S60]` | **ADR REVIEW CANDIDATE**：改为 REJECTED |
| W-2 | ADR-0006 称两条路径都验证了配置变体 | Option B 只读了默认值 | 补 R-1 实验（API 31+ 有公开的 Configuration 重载） |
| W-3 | design/19 把 Resources 在 9–14 上都标为 V（版本敏感） | Option B 的核心 API 自 API 1 起公开，可能比预期更稳定 | 用 R-3、V-3 实验重新标注 |
| W-4 | 「Pure userspace + 仅公开 API」能否运行**任意**第三方 APK | 本轮调研到的所有能运行任意第三方 APK 的成熟项目，都依赖框架层拦截 `[S01][S02][S08][S09]`；只用公开 API 的 Shadow 则要求插件在编译期配合改造 `[S12]` | 见第 6 节的回答 |

## 5. New alternatives（新的候选方向，只记录，不采用）

| 方向 | 说明 | 状态 |
|---|---|---|
| Work Profile + 应用级虚拟化并存 | Work Profile 负责「第二份」，应用级虚拟化只处理它做不到的场景 | SPEC |
| 「合作型 Guest」模式 | 如果产品范围可以限定为「按约定构建的 Guest」，就能走 Shadow 那种纯公开 API 的路线 `[S12]`；这意味着产品定义要改变 | PC |
| Host 的 `AppComponentFactory`（API 28+，PUBLIC） | 可以决定 Host manifest 中组件的实例化方式，以及进程级 ClassLoader `[S60][S65]`；但它**不改变** ActivityThread 为该组件创建的 Context，所以只解决「用哪个类」，不解决「用谁的 Context 和 Resources」 | CPS（API），NE（是否有用） |

## 6. 回答：只用公开 API，能否现实地运行「任意普通第三方 APK」？

**PARTIALLY**。

- **只用公开 API 已被证明可行的部分**：代码加载（EXP-001）、资源表加载（EXP-002 Option B，并有 Shadow 先例）、APK 元数据解析，以及计划中的 Application 实例化。
- **没有找到只用公开 API 的先例、并且大概率需要框架层拦截或系统特权的部分**：未安装组件的启动和生命周期（Activity/Service/Provider/Receiver），Guest 对 PackageManager、ActivityManager 等系统服务的查询结果，以及 native 层的路径与身份。所有能运行任意 APK 的项目都在这些层面做了拦截 `[S01][S02][S08][S09]`，而身份相关的调用即便被拦截，在 Android 12+ 上也只能以 Host 身份与系统交互 `[S37][S38]`。
- **结论的含义**：保持「只用公开 API」的前提下，项目更现实的目标是「在明确列出的兼容范围内运行 Guest」，而不是「运行任意 APK」。是否把框架层拦截纳入项目，需要负责人另行决策，并单独建立 ADR。本文档不做这个决定。

## 7. ADR REVIEW CANDIDATES 汇总

| ADR | 建议 | 依据 |
|---|---|---|
| ADR-0002 | 补充外部证据（AttributionSource 真实案例） | `[S37][S38][S39]` |
| ADR-0004 | 补充 Android 14+ 只读加载的前置条件，待 V-1 实验结果 | `[S18]` |
| ADR-0006 | Option A 改为 REJECTED；Option B 的配置变体与 API 28/29 状态改为「待实验」 | `[S60]`；08 文档 |
