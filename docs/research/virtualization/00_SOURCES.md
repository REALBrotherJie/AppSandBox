# 00 Sources Registry

本文件是 `docs/research/virtualization/` 全部文档的**唯一来源登记表**。其他文档只写 `[Sxx]` 引用，不重复 URL。
新增来源时追加编号，不重排旧编号。

访问日期：除特别说明外均为 **2026-09-23**。

可信度：`PRIMARY` / `SECONDARY-HIGH` / `SECONDARY` / `UNVERIFIED`。
访问状态：`READ`（打开并读取了原页面）/ `SNIPPET`（只看到搜索摘要）/ `BLOCKED`（需登录或 403，未读到正文）/ `LOCAL`（本地 SDK 源码核实）。

## A. 官方 / AOSP（PRIMARY）

| ID | 标题 | 组织 | 日期 | URL | 状态 |
|---|---|---|---|---|---|
| S13 | Android Virtualization Framework API (framework-virtualization README) | AOSP | tag aml_net_351410000 | https://android.googlesource.com/platform/packages/modules/Virtualization/+/refs/tags/aml_net_351410000/libs/framework-virtualization/README.md | READ |
| S14 | Android Virtualization Framework (AVF) overview | source.android.com | 未标注 | https://source.android.com/docs/core/virtualization | READ |
| S15 | Virtual Machine as a core Android Primitive | Android Developers Blog | 2023-12 | https://android-developers.googleblog.com/2023/12/virtual-machines-as-core-android-primitive.html | SNIPPET |
| S16 | Restrictions on non-SDK interfaces | developer.android.com | 持续更新 | https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces | READ |
| S17 | Updates to non-SDK interface restrictions in Android 16 | developer.android.com | 2025 | https://developer.android.com/about/versions/16/changes/non-sdk-16 | SNIPPET |
| S18 | Behavior changes: apps targeting Android 14 | developer.android.com | 2023 | https://developer.android.com/about/versions/14/behavior-changes-14 | READ |
| S19 | Behavior changes: apps targeting Android 15 | developer.android.com | 2024 | https://developer.android.com/about/versions/15/behavior-changes-15 | READ |
| S20 | Behavior changes: apps targeting Android 16 | developer.android.com | 2025 | https://developer.android.com/about/versions/16/behavior-changes-16 | READ |
| S21 | Behavior changes: all apps (Android 16) | developer.android.com | 2025 | https://developer.android.com/about/versions/16/behavior-changes-all | READ |
| S22 | Dynamic Code Loading (security risks) | developer.android.com | 持续更新 | https://developer.android.com/privacy-and-security/risks/dynamic-code-loading | READ |
| S23 | Device and Network Abuse policy | Google Play Console Help | 当前版本 | https://support.google.com/googleplay/android-developer/answer/16559646 | SNIPPET |
| S24 | Use of the broad package visibility (QUERY_ALL_PACKAGES) permission | Google Play Console Help | 当前版本 | https://support.google.com/googleplay/android-developer/answer/10158779 | SNIPPET |
| S25 | Package visibility filtering on Android | developer.android.com | 持续更新 | https://developer.android.com/training/package-visibility | SNIPPET |
| S26 | Prepare your apps for Google Play's 16 KB page size compatibility requirement | Android Developers Blog | 2025-05 | https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html | SNIPPET |
| S27 | Namespaces for native libraries | source.android.com | 未标注 | https://source.android.com/docs/core/permissions/namespaces_libraries | SNIPPET |
| S28 | Seccomp filter in Android O | Android Developers Blog | 2017-07 | https://android-developers.googleblog.com/2017/07/seccomp-filter-in-android-o.html | SNIPPET |
| S29 | seccomp_unotify(2) | man7.org (Linux man-pages) | 当前 | https://www.man7.org/linux/man-pages/man2/seccomp_unotify.2.html | SNIPPET |
| S31 | Support multiple users | source.android.com | 未标注 | https://source.android.com/docs/devices/admin/multi-user | READ |
| S32 | Provision for device management | source.android.com | 未标注 | https://source.android.com/docs/devices/admin/provision | SNIPPET |
| S36 | AttributionSource API reference | developer.android.com | API 31+ | https://developer.android.com/reference/android/content/AttributionSource | SNIPPET |
| S60 | Android SDK sources android-36（本地） | AOSP | SDK 36 | `D:/Company/Install/Android/SDK/sources/android-36/` | LOCAL |
| S62 | Improving Stability with Private C/C++ Symbol Restrictions in Android N | Android Developers Blog | 2016-06 | https://android-developers.googleblog.com/2016/06/improving-stability-with-private-cc.html | SNIPPET |
| S64 | Behavior changes: apps targeting Android 12 | developer.android.com | 2021 | https://developer.android.com/about/versions/12/behavior-changes-12 | SNIPPET |

S60 本地核实过的具体文件（2026-09-23）：
- `content/res/Resources.java:292-300`：`AssetManagerUpdateHandler.onLoadersChanged` 调用 `impl.getAssets().setLoaders(newLoaders)`，**原地修改**底层 AssetManager
- `content/res/Resources.java:353-365`：`Resources.getSystem()` 的底层是 `AssetManager.getSystem()`
- `content/res/AssetManager.java:379-409`：`setLoaders` 重建 ApkAssets 列表
- `app/Application.java:345-348`：`attach()` 中 `mLoadedApk = ContextImpl.getImpl(context).mPackageInfo`
- `app/ContextImpl.java:427-434`：`getImpl` 逐层剥开 ContextWrapper，最后强转为 ContextImpl
- `app/AppComponentFactory.java`：`instantiateClassLoader` / `instantiateApplication` / `instantiateActivity`（public，API 28+）
- `app/Instrumentation.java:1327,1364,1421`：`newApplication` / `callApplicationOnCreate` / `newActivity`（public）
- `app/servertransaction/*`：`ClientTransaction`、`LaunchActivityItem`、`TransactionExecutor` 等（均为 hidden）
- `os/UserManager.java:179-197`：`USER_TYPE_PROFILE_MANAGED/CLONE/PRIVATE`；`createUser`/`createProfile` 需要 `MANAGE_USERS` 或 `CREATE_USERS`，且 `createProfile` 为 `@SystemApi`
- `webkit/WebView.java:2075`：`setDataDirectorySuffix`（WebView 初始化前才能调用）

## B. 项目文档 / 作者材料（SECONDARY-HIGH，属于 PROJECT CLAIM）

| ID | 名称 | 作者/组织 | 最后更新 | URL | License | 状态 |
|---|---|---|---|---|---|---|
| S01 | VirtualApp README（含商业版更新日志） | asLody / 山东合信网络 | 开源代码 2017-12；日志至 2026-09 | https://github.com/asLody/VirtualApp | 开源部分 GPL-3.0*，商业授权 | READ |
| S02 | BlackBox | FBlackBox | 已解散（见 issue #121/#122） | https://github.com/FBlackBox/BlackBox | Apache-2.0 | READ |
| S03 | NewBlackbox | ALEX5402 | 未显示 | https://github.com/ALEX5402/NewBlackbox | Apache-2.0 | READ |
| S04 | NewBlackbox-15 | sudami | 未显示 | https://github.com/sudami/NewBlackbox-15 | Apache-2.0 | READ |
| S05 | ZCore BlackBox | ZENINXOP | 2024–2026 | https://github.com/ZENINXOP/BlackBox | Apache-2.0 | READ |
| S06 | SpaceCore | FSpaceCore | 未显示 | https://github.com/FSpaceCore/SpaceCore | 闭源 SDK，可免费商用 | READ |
| S07 | VirtualSpace | erfansst3（fork 自 chiyuan5） | 未显示 | https://github.com/erfansst3/VirtualSpace | MIT | READ |
| S08 | VirtualXposed + wiki "How does VirtualXposed work" | android-hacker / weishu | wiki 2019-09-18 | https://github.com/android-hacker/VirtualXposed/wiki/How-does-VirtualXposed-work | GPL-3.0* | READ |
| S09 | DroidPlugin | Qihoo360 | 商业站更新至 2024-03 | https://github.com/Qihoo360/DroidPlugin/ | LGPL-3.0 | READ |
| S10 | RePlugin | Qihoo360 | 未查 | https://github.com/Qihoo360/RePlugin | Apache-2.0* | SNIPPET |
| S11 | VirtualAPK | didi | 未查 | https://github.com/didi/virtualapk | Apache-2.0* | SNIPPET |
| S12 | Shadow + `CreateResourceBloc.kt` + issue #751 | Tencent | 未查 | https://github.com/Tencent/Shadow ；https://github.com/Tencent/Shadow/blob/master/projects/sdk/core/loader/src/main/kotlin/com/tencent/shadow/core/loader/blocs/CreateResourceBloc.kt ；https://github.com/Tencent/Shadow/issues/751 | BSD-3-Clause* | SNIPPET |
| S33 | Shizuku | RikkaApps | 未查 | https://github.com/rikkaapps/shizuku | Apache-2.0* | SNIPPET |
| S34 | AndroidHiddenApiBypass / LSPass | LSPosed | 未查 | https://github.com/LSPosed/AndroidHiddenApiBypass | Apache-2.0* | SNIPPET |
| S55 | Frida-Seccomp（通用 svc 跟踪/hook） | Abbbbbi | 未查 | https://github.com/Abbbbbi/Frida-Seccomp | 未查 | SNIPPET |
| S56 | Haven issue #573：chroot-ng（seccomp RET_TRAP→SIGSYS，无 ptrace） | GlassHaven | 未查 | https://github.com/GlassHaven/Haven/issues/573 | — | SNIPPET |
| S57 | injectvm-binderjack | Chainfire | 旧 | https://github.com/Chainfire/injectvm-binderjack | 未查 | SNIPPET |
| S58 | Binder Internals | Android Offensive Security Blog（Google） | 未查 | https://androidoffsec.withgoogle.com/posts/binder-internals/ | — | SNIPPET |
| S59 | Shelter / Insular（F-Droid） | PeterCxy / secure-system | 当前 | https://f-droid.org/packages/net.typeblog.shelter/ ；https://f-droid.org/en/packages/com.oasisfeng.island.fdroid/ | GPL* | SNIPPET |

`*` 表示 License 来自记忆或摘要，**没有打开 LICENSE 文件核实**。任何源码级研究之前必须先核实（见 `27_LICENSE_PROVENANCE.md`）。

## C. 社区 / 失败案例（SECONDARY）

| ID | 标题 | 日期 | URL | 状态 |
|---|---|---|---|---|
| S37 | NewBlackbox issue #1：App crashes on launch on Android 13 | 2025-08-16 | https://github.com/ALEX5402/NewBlackbox/issues/1 | READ |
| S38 | limbusZhCN issue #4：Android 16 Google 登录时 AttributionSource UID 不匹配崩溃 | 2026-09-01 | https://github.com/BBBEANNN/limbusZhCN/issues/4 | READ |
| S39 | VirtualXposed issue #987：given calling package does not match caller's uid | 未查 | https://github.com/android-hacker/VirtualXposed/issues/987 | SNIPPET |
| S35 | WebView 多进程共享数据目录崩溃（多个独立报告） | 2018–2021 | https://github.com/react-native-webview/react-native-webview/issues/968 ；https://github.com/AzureAD/microsoft-authentication-library-for-android/issues/1012 | SNIPPET |
| S61 | 插件化 Activity 占坑 / ClientTransaction 分析（简书等） | 2019–2020 | https://www.jianshu.com/p/aa03c4458b9a ；https://github.com/androidmalin/AndroidComponentPlugin | SNIPPET |
| S65 | AppComponentFactory 介绍（CommonsWare P DP1 随笔） | 2018-03-08 | https://commonsware.com/blog/2018/03/08/random-musings-p-developer-preview-1.html | SNIPPET |

## D. 学术（PRIMARY 论文，SNIPPET 表示只读了摘要）

| ID | 论文 | 会议/年份 | URL | 状态 |
|---|---|---|---|---|
| S40 | App in the Middle: Demystify Application Virtualization in Android and its Security Threats | SIGMETRICS 2019 | https://www.cs.ucr.edu/~zhiyunq/pub/sigmetrics19_app_virtualization.pdf | SNIPPET |
| S41 | Parallel Space Traveling: A Security Analysis of App-Level Virtualization in Android | SACMAT 2020 | https://www.cs.ucr.edu/~heng/pubs/sacmat2020.pdf | SNIPPET |
| S42 | VAHunt: Warding Off New Repackaged Android Malware in App-Virtualization's Clothing | CCS 2020 | https://dl.acm.org/doi/10.1145/3372297.3423341 | SNIPPET |
| S43 | Boxify: Full-fledged App Sandboxing for Stock Android | USENIX Security 2015 | https://www.usenix.org/system/files/conference/usenixsecurity15/sec15-paper-backes.pdf | SNIPPET |
| S44 | NJAS: Sandboxing Unmodified Applications in non-rooted Devices Running stock Android | SPSM@CCS 2015 | https://seclab.cs.ucsb.edu/publications/bianchi2015njas_sandboxing/ | SNIPPET |
| S45 | Mascara: A Novel Attack Leveraging Android Virtualization | arXiv 2020 | https://arxiv.org/abs/2010.10639 | SNIPPET |
| S46 | Risky Cohabitation: Over-privilege Risks of Commodity App Virtualization Platforms in Android | CODASPY 2024 | https://dl.acm.org/doi/abs/10.1145/3626232.3653274 | SNIPPET（ACM 403） |
| S47 | Cells: A Virtual Mobile Smartphone Architecture | SOSP 2011 | https://dl.acm.org/doi/10.1145/2043556.2043574 | SNIPPET |
| S48 | Anception: Application Virtualization For Android | arXiv 2014 | https://arxiv.org/pdf/1401.6726 | SNIPPET |
| S49 | Towards Transparent and Stealthy Android OS Sandboxing via Customizable Container-Based Virtualization | CCS 2021 | https://dl.acm.org/doi/10.1145/3460120.3484544 | SNIPPET |
| S50 | Repack Me If You Can: An Anti-Repackaging Solution Based on Android Virtualization | ACSAC 2021 | https://dl.acm.org/doi/abs/10.1145/3485832.3488021 | SNIPPET |
| S63 | Secure Containers in Android: the Samsung KNOX Case Study | arXiv 2016 | https://arxiv.org/pdf/1605.08567 | SNIPPET |

## D2. 第二轮补充来源（2026-09-23）

| ID | 标题 | 组织/作者 | 类型 | URL | 状态 |
|---|---|---|---|---|---|
| S66 | Build multiple APKs | developer.android.com | PRIMARY | https://developer.android.com/build/configure-apk-splits | SNIPPET |
| S67 | APK signature scheme v3 | source.android.com | PRIMARY | https://source.android.com/docs/security/features/apksigning/v3 | READ |
| S68 | APK signature scheme v2 | source.android.com | PRIMARY | https://source.android.com/docs/security/features/apksigning/v2 | SNIPPET |
| S69 | Chrome Android Sandbox Design | Chromium | SECONDARY-HIGH（厂商设计文档） | https://chromium.googlesource.com/chromium/src/+/refs/tags/128.0.6613.5/docs/security/android-sandbox.md | READ |
| S70 | Isolated Processes and App Zygote Preloading on Android | Mozilla GeckoView | SECONDARY-HIGH | https://firefox-source-docs.mozilla.org/mobile/android/geckoview/project/isolated-process/overview.html | READ |
| S71 | Android 16 release notes | source.android.com | PRIMARY | https://source.android.com/docs/whatsnew/android-16-release | SNIPPET |
| S72 | A Linux VM on Android via AVF（LPC 2025 幻灯片） | Linux Plumbers Conference | SECONDARY-HIGH | https://lpc.events/event/19/contributions/2123/attachments/1730/3786/LPC%202025%20-%20A%20Linux%20VM%20on%20Android%20via%20AVF%20(Android%20MC).pdf | SNIPPET |
| S73 | Android SDK `api-versions.xml`（本地，platforms/android-36） | AOSP | PRIMARY | `D:/Company/Install/Android/SDK/platforms/android-36/data/api-versions.xml` | LOCAL |

第二轮更新的状态：S23、S25 → READ；S43、S44、S48 → READ（仅摘要原文）。
S40、S41 的 PDF 已下载，但本机缺少 PDF 解析工具，只读到摘要级信息，状态仍为 SNIPPET。

S73 查到的 API 级别：`getResourcesForApplication(ApplicationInfo, Configuration)` 31；`ResourcesLoader` 30；`AppComponentFactory` 28；`GET_SIGNING_CERTIFICATES` / `SigningInfo` 28；`PackageInfoFlags` 33；`getVerifiedSigningInfo` 36；`createContextForSplit` 26；`createAttributionContext` 30；`bindIsolatedService` 29；`ZygotePreload` 29；`WebView.setDataDirectorySuffix` 28。

S60 第二轮补充核实：
- `content/pm/PackageManager.java:8983-9030`：`getPackageArchiveInfo` 在带 `GET_SIGNATURES` 或 `GET_SIGNING_CERTIFICATES` 时设置 `PARSE_COLLECT_CERTIFICATES`，即解析时会收集并校验签名
- `content/pm/parsing/ApkLiteParseUtils.java:117-118`：传入的是目录时，按 cluster（base + splits）方式解析
- `dalvik/system/DexPathList.java:59,312,496-509`：`dexPath` / `librarySearchPath` 支持用 `:` 分隔多个条目；native 搜索路径支持 `xxx.apk!/lib/<abi>` 这种 APK 内路径
- `app/ApplicationPackageManager.java:2130-2150`：`getResourcesForApplication` 读取 split 路径、`resourceDirs`、`overlayPaths`、`sharedLibraryFiles`

## E. 无法读取的高价值来源（BLOCKED，需人工补读）

| ID | 标题 | 为什么重要 | URL |
|---|---|---|---|
| S52 | [原创] AndProxy 升级版：基于 Seccomp 的无 Hook Binder 服务代理（看雪，2026-05，据搜索摘要） | 2026 年公开的 seccomp Binder 路由方案，web-search-01 点名要求调研 | 看雪 Android 安全版（搜索摘要，未拿到帖子 ID） |
| S53 | [原创]【Android】深入底层 Binder 拦截（看雪） | native Binder 拦截的实现层级 | https://bbs.kanxue.com/thread-279725.htm |
| S54 | 基于 seccomp 的 Android 通用 svc hook 方案（知乎） | 无 root 情况下的 seccomp 拦截可行性 | https://zhuanlan.zhihu.com/p/638442453 |

处理办法：人工登录后阅读，把要点补到 `05_BINDER_INTERCEPTION_TECHNIQUES.md`，并把状态从 BLOCKED 改为 READ。
