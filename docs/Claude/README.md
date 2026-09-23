# VirtualApp 现代化经验知识库（HuangJiang 个人修改）

> 来源：`D:\WorkSpace\Android\MySelf\VirtualApp` 中由 HuangJiang 完成的现代化改造
> （阶段 1–17 + GMS 立项，2026-09-18 ~ 2026-09-23）。原项目因专利原因停止继续。
> 本目录把这些修改**以工程知识（问题 → Android 机制 → 根因 → 方案）的形式**沉淀，
> 供 AppSandBox 后续独立设计复用。

## 重要：与本仓库 clean-room 政策的关系

AppSandBox 的 `docs/CLEAN_ROOM.md` 明确声明：不使用 VirtualApp 等项目的源码，
本阶段不含任何 Binder/AMS/PMS hook、hidden API 绕过、native hook、运行时启动或反检测。

因此本目录：

- **只记录"遇到什么问题、Android 为什么这样、根因在哪、解决思路是什么"**，不搬运 VirtualApp 源码，
  不作为"照抄清单"。要落地到 AppSandBox，须按本仓库既定流程重新独立设计：
  需求 → Android 机制分析 → AOSP/API 研究 → 实验 → 独立设计 → 实现 → 测试。
- 这些结论多数是**通用的 Android 平台事实**（例如 Android 12 的 AttributionSource 校验、
  Android 9+ 用 `signingInfo` 而非 `signatures`、现代打包 `extractNativeLibs=false` 的后果），
  与具体实现无关，可直接作为设计输入。
- 涉及反检测、签名伪造、GMS 绕过的内容属于**未来阶段**（当前 clean-room 阶段明确排除），
  仅作为"若将来进入运行时/兼容对抗阶段会遇到的墙"的前瞻记录。

## 测试基线

所有实测结论均在：**小米 10 / Android 12 (API 31) / arm64-v8a**。
其余 Android 版本（13–16）与其它设备均无实机证据。

## 文档地图

| 文档 | 主题 | 阶段来源 |
|---|---|---|
| [01-构建工具链现代化.md](./01-构建工具链现代化.md) | AGP/Gradle/NDK/AndroidX 迁移、native 编译修复 | 阶段 1 |
| [02-arm64-inline-hook与IO重定向.md](./02-arm64-inline-hook与IO重定向.md) | 自研 arm64 inline hook、IO 重定向真正生效的前置条件 | 阶段 5、6、7 |
| [03-Android12+运行时兼容.md](./03-Android12+运行时兼容.md) | AttributionSource、Provider/广播/服务/Job 改名与参数漂移 | 阶段 2、3、4、13、16 |
| [04-包解析签名与网络.md](./04-包解析签名与网络.md) | PackageParser 版本适配、signingInfo、明文/NSC、动态广播权限 | 阶段 2、4、9、14 |
| [05-反检测与路径隐藏.md](./05-反检测与路径隐藏.md) | dataDir/sourceDir/proc maps/mounts 泄露与消除边界 | 阶段 5、6、8、10 |
| [06-多开与进程保活.md](./06-多开与进程保活.md) | 多 userId 多开机制、前台服务 START_STICKY 重启 | 阶段 11、12 |
| [07-GMS-MicroG方案.md](./07-GMS-MicroG方案.md) | MicroG + 签名伪造 + uid 归因让 Firebase/FCM 通过 | GMS 立项 |
| [08-复杂应用抽测结论.md](./08-复杂应用抽测结论.md) | 酷安/微博/京东/微信/WPS/Unity 游戏的墙与结论 | 阶段 13–17 |
| [09-UI改版-个人应用空间.md](./09-UI改版-个人应用空间.md) | 首页/添加应用/多选模型的可复用设计 | 阶段 22 (UI) |

## 一句话总纲

VirtualApp 这类"宿主 uid 多开 + 路径重定向"沙箱，在现代 Android（12+/arm64/高 targetSdk）
上的主要战场有四类：

1. **native hook 基座**必须先真正在 arm64 上工作，否则文件隔离、路径反检测全是空谈（阶段 5–6 是枢纽）。
2. **系统服务接口随版本改名/加参**（bindService→bindServiceInstance、broadcastIntent→…WithFeature、
   getIntentSender→…WithFeature 等），代理必须按类型定位参数而非固定下标。
3. **Android 12 的身份校验**（AttributionSource uid 必须等于真实 binder uid）会击穿一切"未被包装"的调用路径。
4. **深度对抗**（原生反篡改、反 VA 自退、GMS 硬件认证）是 app 专属大工程，非通用 bug，通常不值得攻。
