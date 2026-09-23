# 08 Resources Strategies

状态标签：`CPS`=CONFIRMED BY PRIMARY SOURCE，`MIR`=MULTIPLE INDEPENDENT REPORTS，`PC`=PROJECT CLAIM，`SSS`=SINGLE SECONDARY SOURCE，`SPEC`=SPECULATIVE，`NE`=NEEDS EXPERIMENT。来源编号见 `00_SOURCES.md`。

## 1. 结论先行

| 问题 | 结论 | 状态 |
|---|---|---|
| 未安装 APK 的资源，公开 API 能否建立独立资源空间？ | 能：`getPackageArchiveInfo` → 修改 `ApplicationInfo.sourceDir/publicSourceDir` → `PackageManager.getResourcesForApplication` | CPS（S60 源码）+ EXP-002 真机验证 |
| Option B 是否有公开先例？ | **有**。Tencent Shadow 的 `CreateResourceBloc` 用的就是这条路径 | PC `[S12]` |
| `ResourcesLoader` 能否用来隔离？ | **不能**。`addLoaders` 会原地修改传入的 AssetManager；以 `Resources.getSystem().assets` 为底座会污染整个进程的系统 AssetManager | CPS `[S60]` |
| Option B 能否做配置变体（横屏等）？ | API 31+ 有公开重载 `getResourcesForApplication(ApplicationInfo, Configuration)` | CPS `[S60]`（api-versions.xml since=31），真机未测 → NE |
| API 28/29 能否走 Option B？ | `getResourcesForApplication(ApplicationInfo)` 自 API 1 起公开；Shadow 在更老的版本上也用它 | CPS（API）+ PC `[S12]`，真机未测 → NE |

## 2. 各路线对比

| 路线 | API 级别 | 公开性 | 是否隔离 Host | name→ID（getIdentifier） | 配置变体 | 主要风险 |
|---|---|---|---|---|---|---|
| **B：archive ApplicationInfo + `getResourcesForApplication`** | 1+（带 Configuration 的重载 31+） | PUBLIC | 是（独立 AssetManager：framework + Guest） | 可用（EXP-002 实测） | 31+ 用重载；28–30 待定 | 由 ResourcesManager 按路径缓存，替换同路径 APK 时可能读到旧缓存 |
| A：`Resources(systemAssets,…)` + `ResourcesLoader` | 30+ | 构造函数已弃用；loader 为 PUBLIC | **否**：会修改 `AssetManager.getSystem()` | 可用，但范围是整个进程 | 会改写共享 AssetManager 的配置 | 污染全局、多个 Guest 相互冲突、loader 不断累积 |
| Legacy：Host assets + `ResourcesLoader` | 30+ | 同上 | **否**：与 Host 包组合并，并修改 Host 的 AssetManager | 0（包组以 Host 包名登记） | 按合并后的包组选取，结果会串 | EXP-002 第一轮失败的根因 |
| `AssetManager.addAssetPath` | 旧版本 | HIDDEN | 取决于用法 | — | — | 非 SDK 接口；项目策略不采用 `[S16]` |
| 修改插件资源的 package ID（插件框架常见做法） | 构建期 | — | 是 | 可用 | 可用 | 需要重新构建 Guest，**不适用于任意第三方 APK** |

`[S60]` 核实过的关键源码：
- `Resources.java:292-300`：`onLoadersChanged` → `impl.getAssets().setLoaders(newLoaders)`
- `Resources.java:363`：`Resources.getSystem()` 使用 `AssetManager.getSystem()`
- `ApplicationPackageManager.getResourcesForApplication`：调用 `getTopLevelResources(sourceDir 或 publicSourceDir, split 路径, resourceDirs, overlayPaths, sharedLibraryFiles, …)`。其中 `sameUid = (app.uid == Process.myUid())` 决定取 `sourceDir` 还是 `publicSourceDir`，所以两个字段都要设成 Guest APK 路径（EXP-002 已这样做）

## 3. Option B 需要补齐的细节

1. **split APK**：`getTopLevelResources` 会读取 `splitSourceDirs`/`splitPublicSourceDirs`。导入 split 包时，需要把各个 split 的路径填进 ApplicationInfo。目前只导入 `base.apk`，见 `23_SPLIT_APK_SUPPORT.md`（待写）。状态：CPS（参数存在），NE（行为）。
2. **WebView 共享库**：Shadow 在创建插件资源前会先初始化一次 WebView，目的是让 WebView 相关逻辑把 WebView APK 加进 `sharedLibraryFiles`，否则插件里的 WebView 会缺资源。PC `[S12]`（Shadow issue #751）。AppSandbox 以后支持 WebView Guest 时需要复现这个问题。NE。
3. **缓存与替换**：`getTopLevelResources` 由 ResourcesManager 管理并缓存。ADR-0003 规定每个版本使用不可变路径，这正好能避开「同一路径换了内容却读到旧缓存」的问题。SPEC → NE。
4. **配置变体**：API 31+ 使用 `getResourcesForApplication(app, configuration)`，**不要**再用 `Resources(sharedAssets, dm, config)` 这种共享 AssetManager 的写法。注意 `PackageManager` 基类的默认实现会忽略 configuration，真正生效的是 `ApplicationPackageManager` 的重写。CPS `[S60]`。
5. **主题与 inflate**：拿到 Resources 只解决了「资源表」。Theme 和 `LayoutInflater` 还依赖 Context，属于 EXP-003 的边界。

## 4. 对 ADR-0006 的评审建议（ADR REVIEW CANDIDATE，不直接修改）

- Option A 应从「API 30+ baseline」降级为 **REJECTED：会修改进程全局 AssetManager**。证据：`[S60]` 以及 EXP-002 日志里的 `guestAssetsEqualsSystem=true`。
- 「Option B 已验证配置变体」这一说法没有证据（Runner 只读了默认值），需要补做实验。
- 「Host 未被污染」依赖的 `hostFingerprint()` 看不到 loader 污染，需要换成直接的检查。

## 5. 建议的补充实验（不执行）

| 编号 | 内容 | 通过条件 |
|---|---|---|
| R-1 | Option B + `getResourcesForApplication(app, landscapeConfig)` | 同一个 ID 在两个 Resources 下分别返回 `EXP002_DEFAULT` 和 `EXP002_LANDSCAPE` |
| R-2 | 在**全新进程**里只跑 Option B | Host 资源目录、`Resources.getSystem().getIdentifier(…, guestPkg)` 都不出现 Guest 内容 |
| R-3 | API 28/29 设备或模拟器上跑 Option B | string/raw/asset/layout 全部通过 |
| R-4 | 导入两个都用 `0x7f` 的 Guest，分别建立 Option B Resources | 两者互不可见 |
| R-5 | 同一个 Guest 的 v1 和 v2 使用不同路径，分别建立 Resources | 读到各自版本的值，没有串缓存 |
