# 07 GMS in Sandbox：MicroG + 签名伪造方案（GMS 立项）

> ⚠️ 签名伪造/GMS 绕过属于 AppSandBox clean-room 阶段**排除**的能力。本文为前瞻记录。

## 为什么真 GMS 在沙箱里跑不起来

真正的 Google Play Services 会校验**自身 apk 的真实性/签名**和**调用方身份**。
在沙箱里（宿主 uid、改过的路径、非系统应用）它判定自己无效：
`Invalid GmsCore APK`、`SERVICE_INVALID`、`DEVELOPER_ERROR`，`GoogleSignatureVerifier` 失败。
硬门槛依赖 GMS 的 app（如 Firebase `CheckAndFixDependencies` 必须成功的 Unity 游戏、
等 `com.google.android.gsf.gservices` provider 的微信）会卡死或崩溃。

**结论**：让真 GMS 在沙箱内可用是独立大工程，不值得。可行路线是 **MicroG + 签名伪造**。

## 为什么 MicroG 方案能成

MicroG 的 GmsCore(`com.google.android.gms`) 和 FakeStore(`com.android.vending`)
清单里**自带** `android.permission.FAKE_PACKAGE_SIGNATURE` 权限 + `<meta-data name="fake-signature">`
（值为 Google 的签名）。沙箱对声明了该权限+元数据的包，报告其元数据里写的伪造签名。
于是沙箱把 MicroG 的 GmsCore **报告成 Google 签名**，guest 的 `GoogleSignatureVerifier` 认可。

## 让它端到端工作的三块拼图

1. **FAKE_PACKAGE_SIGNATURE 机制**（VA 既有）：解析时对声明该权限的包报告伪造签名。
2. **signingInfo**（见 [04](./04-包解析签名与网络.md)）：`generatePackageInfo` 要填 `PackageInfo.signingInfo`，
   Android 9+ 校验读它而非 `signatures`。缺了它 GMS 拿不到签名。
3. **uid 归因（G3，关键摩擦点）**：MicroG 服务按调用方 uid 校验其声明的包名
   （`getPackagesForUid(callingUid)` 是否含 packageName）。**沙箱里所有 guest 共享宿主 uid**，
   于是 MicroG 进程里 `getPackagesForUid(宿主uid)` 只返回 `com.google.android.gms`，认不出真正调用的 guest：
   ```
   SecurityException: UID [<host>] is not related to packageName [<guest pkg>]
   ```
   FCM 注册、GAID 等"调用方=某具体 app"的服务全失败。
   - **解决**：`getPackagesForUid` 代理——当**当前进程属于 GMS 家族**
     （`com.google.android.gms`/`gsf`、`com.android.vending`）时，把**所有已安装 guest 包**并入返回集合。
     只在 GMS 进程内放宽，普通 app 不变，风险可控。

## 装入步骤（调试入口）

新增调试广播 `INSTALL_MICROG --es dir <目录>`：先卸载沙箱内的 GMS 家族，
再把该目录下所有 `*.apk`（GmsCore/GsfProxy/FakeStore）装入沙箱。

- MicroG 组件：GmsCore + FakeStore（github.com/microg/GmsCore releases，实测 v0.3.16），
  GsfProxy（github.com/microg/GsfProxy releases，v0.1.0，提供 `gservices` provider）。
- 验证成功标志：日志 `Using fake-signature feature on : com.google.android.gms`、
  `ProviderInstaller: Installed default security provider GmsCore_OpenSSL`、无 `is not related to packageName`。
- MicroG apk 体积大，**不入库**，按 URL 下载后 push 到设备再装入。

## 成果与局限

- **打通**：Firebase 依赖检查通过（Unity 游戏过了此前的 40% 墙）、ProviderInstaller、GServices、FCM 注册流程推进。
- **MicroG 不提供**：SafetyNet / SafeBrowsing / Play Integrity 等硬件/远程认证（返回 `API_DISABLED`/`SERVICE_INVALID`，
  一般被 app 优雅降级）。
- **无关 GMS 的残留**：有些 app 仍因其它沙箱问题失败（如广告 SDK 绑定死亡服务、app 自身多进程/反篡改）。

## 给 AppSandBox 的启示

- "沙箱单 uid 多 app" 与 "GMS 按调用方 uid 校验包名" 是根本摩擦，任何依赖 GMS 的场景都会撞上，
  且只能在 GMS 进程内做定向放宽。
- 签名伪造依赖 `signingInfo` 正确填充——两者是绑定的。
- 硬件/远程认证（SafetyNet/Play Integrity）在沙箱内基本无解，只能寄望 app 优雅降级。
