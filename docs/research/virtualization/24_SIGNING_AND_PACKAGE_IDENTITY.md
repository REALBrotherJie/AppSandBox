# 24 Signing and Package Identity

状态标签：`CPS`=CONFIRMED BY PRIMARY SOURCE，`MIR`=MULTIPLE INDEPENDENT REPORTS，`SPEC`=SPECULATIVE，`NE`=NEEDS EXPERIMENT。来源编号见 `00_SOURCES.md`。

## 1. 获取未安装 APK 的签名：公开 API 就够用

| 方法 | API 级别 | 行为 | 状态 |
|---|---|---|---|
| `getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)` → `PackageInfo.signingInfo` | 28+ | 解析时设置 `PARSE_COLLECT_CERTIFICATES`，会收集并**校验**签名；校验失败时返回 null | CPS `[S60][S73]` |
| `PackageManager.getVerifiedSigningInfo(path, minScheme)` | 36（FlaggedApi） | 不要求文件是完整的应用包，可以指定最低签名方案版本 | CPS `[S60][S73]` |
| 自己解析签名块 | — | 不需要：平台 API 已经覆盖 | — |

签名方案：v1（JAR）、v2（Android 7.0+）、v3（Android 9+，支持密钥轮换）。v3 的轮换证明是一条证书链，旧证书对新证书签名。Android 13 起 `checkSignatures` 会识别轮换链。CPS `[S67][S68]`

对 Package Registry 的含义：
- 导入时就记录 `signingInfo`：当前签名者、是否有多个签名者、历史证书链。
- 同一个包的新版本导入时，要求「新签名者 = 旧签名者，或者在旧签名者的轮换链上」，这样才与系统升级的语义一致。SPEC（设计建议）

## 2. Guest 查询自身身份时发生什么

Guest 代码常见的写法是 `getPackageManager().getPackageInfo(getPackageName(), GET_SIGNING_CERTIFICATES)`，用来检查自己的签名、版本或安装来源。

| Guest 的调用 | 如果不做翻译 | 状态 |
|---|---|---|
| `getPackageInfo(guestPkg, …)` | 系统 PMS 里没有 Guest，抛 NameNotFoundException | CPS（Guest 未安装，PMS 无记录） |
| `getPackageInfo(getPackageName(), …)`，而 Context 返回的是 Host 包名 | 拿到的是 **Host 的签名和版本** | CPS（推导），NE |
| `getInstallSourceInfo` / `getInstallerPackageName` | 同上，Guest 查不到或拿到 Host 的信息 | NE |

结论：
- Registry 里已经有 Guest 的真实签名和版本，所以「返回什么」是清楚的；问题在于「Guest 的这些调用能不能被导向 Registry」。这属于 PackageManager 翻译层（`09_PACKAGE_MANAGER_VIRTUALIZATION.md`）。
- 按 ADR-0002，系统边界上的身份仍然是 Host。**任何需要系统或服务器端验证的身份证明**（例如平台完整性证明、硬件支持的密钥证明）都只能反映 Host 的真实身份，这是设计上的限制，不应尝试改变。

## 3. 建议实验（不执行）

| 编号 | 内容 | 通过条件 |
|---|---|---|
| SG-1 | 对 GuestTestApp 调用 `getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)` | 拿到的证书摘要与 `apksigner verify --print-certs` 一致 |
| SG-2 | 篡改 APK 中的一个字节后再解析 | 返回 null（签名校验失败），并且被导入流程拒绝 |
| SG-3 | 在 Guest 代码里用 Controlled Context 调用 `getPackageManager().getPackageInfo(getPackageName(), …)` | 记录实际拿到的是谁的信息（观测，不修复） |
