# Activity Runtime Architecture

状态：DESIGN ONLY / PROPOSED，2026-09-24。task-17 不实现 Activity runtime、Stub pool、Hook、Binder 或 hidden API。

## Decision

普通未安装 APK 的任意 Activity 不能只靠 Public SDK 变成 system-real Activity。推荐高兼容研究路线：**Manifest Stub + 受控 client-side restore，必要时再叠加 Instrumentation/ClientTransaction interception**。Binder proxy 不是第一实验必需项。

## Preferred chain

```text
Guest Intent
 -> virtual resolver finds Guest ActivityInfo
 -> allocate VirtualActivityRecord + persistent launch id
 -> select compatible Host Stub
 -> preserve logical Guest Intent in controlled record
 -> send Host Stub Intent to ATMS
 -> system creates Host ActivityRecord/Task/token/window
 -> client receives Host ClientTransaction
 -> restore Guest record by launch id/token
 -> instantiate Guest Activity with Guest ClassLoader
 -> build version adapter for Guest context/resources/application
 -> translate lifecycle, intent, result, back and state
```

system_server remains convinced it launched Host Stub. Guest identity is logical unless the OS is modified.

## Conceptual VirtualActivityRecord

不要在本轮创建 production class。未来记录应至少包含：`guestPackage`, `guestComponent`, `revisionId`, `instanceId`, `stubComponent`, `launchId`, `realActivityToken`, `taskId`, `guestActivityInfo`, `originalIntent`, `logicalTaskAffinity`, `launchMode`, `lifecycleState`, `savedStateVersion`。Token map 必须有持久 launch record，不能只存在内存。

## Context and resources

Guest ClassLoader/APK 已由 GuestStore 提供。Guest Resources 必须按 Guest archive/configuration 构造；Window/token 仍 Host-owned。资源 wrapper 不能自动修复 `getApplication()`, `getPackageName()`, `ActivityInfo` 或 `mLoadedApk`。更真实的 Guest Application 需要显式 controlled Application 或后续 LoadedApk/ContextImpl integration。

## Route policy

- A 是稳定 fallback/control。
- C 取得最小真实 Host token。
- E 是 Host transaction 到 Guest object 的关键桥，但依赖 non-SDK internals。
- G 是高兼容候选，状态只能 `PROPOSED`。
- H 是后续 ContextImpl/LoadedApk fidelity 研究，不是首个依赖。
- F/J 单独不足。

## Non-goals

不实现 production Activity、Stub pool、组件 Hook、Binder interception、hidden API、Activity.attach、parser 扩展、split Activity、permission virtualization、WebView/JNI/native test。
