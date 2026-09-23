# 23 Split APK Support

状态标签：`CPS`=CONFIRMED BY PRIMARY SOURCE，`SSS`=SINGLE SECONDARY SOURCE，`SPEC`=SPECULATIVE，`NE`=NEEDS EXPERIMENT。来源编号见 `00_SOURCES.md`。

## 1. 背景

- 以 split 方式分发的应用，由一个 base APK 加上零个或多个 split APK 组成；常见的 split 类型是 ABI（native 库）、密度（图片）和语言（字符串），另外还有按需下载的 dynamic feature 模块。CPS `[S66]`
- 系统安装 split 包时，所有 APK 必须包名相同、versionCode 相同、签名证书相同，并且只能有一个 base。SSS（安装器相关资料的摘要）
- `.apks` / `.xapk` / `.apkm` 是第三方工具打包 split 的容器格式，**不是** Android 官方格式。SSS

## 2. 只导入 `base.apk` 的后果

| split 类型 | 缺了会怎样 | 严重程度 |
|---|---|---|
| ABI split | 找不到 native 库，`System.loadLibrary` 失败 | 高：涉及 native 的应用直接无法运行 |
| 语言 split | 缺少对应语言的字符串，回退到默认语言，或者找不到资源 | 中 |
| 密度 split | 缺少对应密度的图片，回退或找不到资源 | 中 |
| dynamic feature | 缺少代码和组件；应用通常会走自己的按需下载流程（依赖 Play 服务） | 高：功能缺失 |

结论：现代应用如果从商店抽取出来，**多数是 split 包**。只支持 `base.apk` 的方案只适用于测试 APK 和旧式单包应用。SPEC（「多数」来自普遍经验，没有统计数据）。

## 3. 公开 API 的支撑情况

| 需要做的事 | 公开能力 | 状态 |
|---|---|---|
| 解析元数据 | `getPackageArchiveInfo` 如果传入目录，会按 cluster（base + splits）解析 | CPS `[S60]`（`ApkLiteParseUtils.java:117`）；公开文档没有写明支持目录，行为 NE |
| 加载代码 | `DexClassLoader` 的 `dexPath` 可以用 `:` 列出多个 APK | CPS `[S60]` |
| 加载 native 库 | `librarySearchPath` 可以指向解压目录，或者 `split.apk!/lib/<abi>` 这种 APK 内路径 | CPS `[S60]`；APK 内路径要求 .so 未压缩并按页对齐，NE |
| 建立资源 | Option B：把 split 路径填进 `ApplicationInfo.splitSourceDirs` / `splitPublicSourceDirs` | CPS `[S60]`；NE |
| `isolatedSplits`（每个 split 独立 ClassLoader） | 系统对已安装应用的实现依赖 LoadedApk；`createContextForSplit`（API 26）只适用于**已安装应用自己的** split | CPS（API）；对 Guest 不适用 |

## 4. 设计建议（不实现）

1. **Package Registry**：一个 Guest 版本由「一组 APK」组成（base + splits），整体不可变，整体计算摘要。这与 ADR-0003 的方向一致。
2. **导入校验**：导入时检查包名、versionCode、签名证书三者一致，并且只有一个 base；可以直接复用 `getPackageArchiveInfo(…, GET_SIGNING_CERTIFICATES)`，见 `24_SIGNING_AND_PACKAGE_IDENTITY.md`。
3. **ABI 选择**：只保留与设备 ABI 匹配的 split，并记录选择结果。
4. **dynamic feature**：第一阶段明确标为不支持。

## 5. 建议实验（不执行）

| 编号 | 内容 | 通过条件 |
|---|---|---|
| SP-1 | 让 GuestTestApp 额外产出一个语言 split（或 ABI split），放在同一个目录里，用 `getPackageArchiveInfo(目录)` 解析 | 能拿到 split 名称；如果不支持，记录真实的失败方式 |
| SP-2 | Option B 在 `splitSourceDirs` 中加入语言 split | 切换 locale 后读到 split 里的字符串 |
| SP-3 | `DexClassLoader` 的 `dexPath` 同时包含 base 和 feature split | 能加载 split 里的类 |
