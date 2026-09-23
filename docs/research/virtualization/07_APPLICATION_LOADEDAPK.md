# 07 Application / LoadedApk

状态标签：`CPS`=CONFIRMED BY PRIMARY SOURCE，`PC`=PROJECT CLAIM，`SSS`=SINGLE SECONDARY SOURCE，`SPEC`=SPECULATIVE，`NE`=NEEDS EXPERIMENT。来源编号见 `00_SOURCES.md`。

## 1. 框架里的关系（均为 CPS `[S60]`，android-36 源码）

```text
ActivityThread
  └─ LoadedApk（每个已安装包一份：包名、ClassLoader、Resources、Application 单例）
       └─ ContextImpl（mPackageInfo = LoadedApk）
            └─ Application（ContextWrapper，mBase = ContextImpl）
                 └─ Application.mLoadedApk
```

- `Application.attach(Context)`（`Application.java:345-348`）：
  ```java
  attachBaseContext(context);
  mLoadedApk = ContextImpl.getImpl(context).mPackageInfo;
  ```
- `ContextImpl.getImpl(Context)`（`ContextImpl.java:427-434`）：沿着 `ContextWrapper.getBaseContext()` 一路剥开，最后**强转为 ContextImpl**。
- `ContextImpl.getApplicationContext()`（`ContextImpl.java:477-480`）：返回 `mPackageInfo.getApplication()`，也就是 **LoadedApk 登记的那个 Application**。
- `ContextImpl.createPackageContextAsUser`（`ContextImpl.java:2835+`）：通过 `mMainThread.getPackageInfo(packageName, …)` 查询已安装的包，对未安装的 Guest 无法使用。
- `Instrumentation.newApplication(ClassLoader, String, Context)` 和 `callApplicationOnCreate(Application)` 都是 PUBLIC API（`Instrumentation.java:1327,1364`）。
- `AppComponentFactory.instantiateApplication`（API 28+，PUBLIC）只作用于 **Host 自己**的 Application，不能用来创建 Guest Application。

## 2. 对 EXP-003 的直接影响

| 场景 | 后果 | 状态 |
|---|---|---|
| Controlled Context 是 ContextWrapper，最底层是 Host 的 ContextImpl | Guest Application 的 `mLoadedApk` = **Host 的 LoadedApk** | CPS（源码推导），NE（运行时观测） |
| Controlled Context 的 base 为 null，或最底层不是 ContextImpl | `attach` 时 `getImpl` 强转失败，抛 ClassCastException | CPS（源码推导），NE |
| Guest 代码调用 `getApplicationContext()`，而 Context 没有重写这个方法 | 返回 **Host 的 Application** | CPS |
| 用 `createPackageContext(guestPkg)` 为 Guest 构造 Context | 抛 NameNotFoundException（Guest 未安装） | CPS（源码推导），NE |

目前框架里读取 `Application.mLoadedApk` 的地方很少：`ActivityThread` 在 attach agent、调试登记等场景会用到，`Application.getLoadedApkInfo()` 用来输出诊断信息。所以短期影响主要是**诊断信息和少数框架路径拿到 Host 身份**，不会直接导致崩溃。SPEC → NE。

## 3. 理论路线

| 路线 | 做法 | 公开性 | 优点 | 缺点 |
|---|---|---|---|---|
| R1 Instrumentation + ContextWrapper（EXP-003 当前方案） | `newApplication(guestLoader, cls, controlledCtx)` | PUBLIC | 不需要 hidden API | `mLoadedApk` 指向 Host；需要逐个重写 Context 的 getter |
| R2 直接调用构造函数 | 用 guestLoader 加载类后 `newInstance()` | PUBLIC | 只作为对照组 | 没有 base Context，基本不可用 |
| R3 自建 LoadedApk / ContextImpl | 反射框架内部 | HIDDEN | 语义最接近真实安装 | 违反项目的 hidden API 策略；各版本差异大 |
| R4 Host 的 `AppComponentFactory` | 在 Host 进程启动时替换 Host 的 ClassLoader / Application | PUBLIC（API 28+） | 可在进程级统一 ClassLoader | 只作用于 Host 自己的 Application；每个进程只有一次 |

插件框架的公开资料（Shadow 等）也采用「插件 Application 由框架自己构造，Context 由框架包一层」的思路 PC `[S12]`，但 Shadow 会在编译期改写插件代码，**不适用于任意第三方 APK**。

## 4. 建议写入 EXP-003 文档的内容（ADR REVIEW CANDIDATE，不直接修改）

1. `EXP-003-HOST-LEAKAGE-MATRIX.md` 增加一行：`Application.mLoadedApk`，期望 Guest 语义，实际是 Host 的 LoadedApk，处置为「必须披露 / 观测」。
2. `EXP-003B` 的前置条件写明：Controlled Context 最底层必须是一个真实的 ContextImpl，否则 `attach` 会崩溃。
3. `EXP-003A` 的负面对照增加一项：`getApplicationContext()` 不能返回 Host Application（已有），并记录 `ContextImpl.getImpl(ctx).mPackageInfo` 的包名（只做观测，不修改）。

## 5. 建议实验（不执行）

| 编号 | 内容 | 观测 |
|---|---|---|
| L-1 | 用 R1 创建 Guest Application，不调用 onCreate | `app.baseContext` 链、`getApplicationContext()`、`getPackageName()`、`getResources()`、`getClassLoader()` 分别返回谁 |
| L-2 | 用一个 base 为 null 的 ContextWrapper 调用 `newApplication` | 是否在 `attach` 时抛 ClassCastException，异常出现在哪一步 |
| L-3 | 同一进程里创建两个 Guest Application | 静态状态、`getApplicationContext()` 是否串到另一个实例 |
