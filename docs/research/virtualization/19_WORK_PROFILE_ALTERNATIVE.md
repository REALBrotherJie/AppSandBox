# 19 Work Profile Alternative

状态标签：`CPS`=CONFIRMED BY PRIMARY SOURCE，`MIR`=MULTIPLE INDEPENDENT REPORTS，`PC`=PROJECT CLAIM，`SSS`=SINGLE SECONDARY SOURCE，`SPEC`=SPECULATIVE，`NE`=NEEDS EXPERIMENT。来源编号见 `00_SOURCES.md`。

## 1. 机制

- 普通应用可以作为 DPC（Device Policy Controller），通过 `DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE` 发起创建 Work Profile；用户确认后，这个应用成为该 profile 的 Profile Owner。CPS `[S32]`
- Managed profile 是 Android 多用户体系里的一种 profile 用户类型，与主用户并存。CPS `[S31]`
- 开源实现：Shelter、Island / Insular 就是利用 Work Profile 做应用隔离和双开的。PC `[S59]`
- 其他 profile 类型：
  - Clone profile 已存在于用户类型列表（`USER_TYPE_PROFILE_CLONE`），但 AOSP 明确表示「不提供端到端支持，需要 OEM 定制」。CPS `[S31]`
  - `UserManager.createProfile` 是 `@SystemApi`，`createUser` 需要 `MANAGE_USERS` 或 `CREATE_USERS`，普通应用不能自己创建。CPS `[S60]`
  - Android 15 的 Private Space 由用户在系统设置里创建，普通应用不能代为创建。SSS（搜索摘要）

## 2. 与应用级虚拟化对比

| 维度 | Work Profile | 应用级虚拟化（AppSandbox 路线） |
|---|---|---|
| Guest 安装 | 真实安装到 profile 用户下（PMS 可见） | 不安装，由 Host 自己登记 |
| UID | 每个应用有真实的独立 UID（profile 用户下） | 所有 Guest 共用 Host UID（ADR-0002） |
| SELinux / 数据目录 | 系统原生隔离 | 只能逻辑隔离 |
| Activity / Service / 通知 / Provider | 系统原生支持，兼容性等同正常安装 | 需要 stub 组件和系统服务翻译，逐版本维护 |
| AttributionSource / AppOps | 身份天然正确 | 已有多个真实的崩溃案例，见 `26_GAPS` 第 3 节 `[S37][S38]` |
| 实例数量 | 受系统 profile 数量限制（一般认为只能有一个 Work Profile） | 理论上不限 |
| 与公司 MDM 共存 | 会冲突（设备上已有公司 Work Profile 时通常无法再建） | 不冲突 |
| 用户体验 | 需要走一遍系统创建流程；profile 里的应用带公文包角标 | 在 Host 界面内完成 |
| Play 合规 | 常规 DPC 行为，风险较低 | 动态加载代码存在政策风险 `[S23][S22]` |
| 开发与维护成本 | 低（主要是 DPC 管理界面） | 很高（每个 Android 大版本都要适配） |

「只能有一个 Work Profile」和「与公司 MDM 冲突」这两行来自普遍认知，本轮没有找到一手文档佐证，状态为 **SPEC → NE**。

## 3. 回答：「双开 / 多账户」产品哪个更合理？

- 如果产品目标**只是**让同一个已安装应用多开一份（第二个账号）：Work Profile 在身份、兼容性、合规和维护成本上都占优，主要短板是**实例数量**和**创建流程的体验**。
- 如果需要任意多个实例、运行**未安装**的 APK、或完全在 Host 内控制 Guest 的界面和生命周期：只能走应用级虚拟化，代价是长期的兼容性维护，以及在 UID 和系统服务身份上的根本限制。
- 这两条路线**不互斥**。一个务实的产品形态是：用 Work Profile 做「第二份」，应用级虚拟化只用于 Work Profile 做不到的场景。

## 4. 建议实验（不执行）

| 编号 | 内容 | 观测 |
|---|---|---|
| W-1 | 在 API 31 设备上，从测试 DPC 发起 `ACTION_PROVISION_MANAGED_PROFILE` | 创建流程、需要用户做几步操作、Guest 能否直接安装到 profile |
| W-2 | 已有 Work Profile 时再次发起创建 | 系统的真实报错或拒绝方式，用来核实「只能有一个」 |
| W-3 | 在 profile 中安装 GuestTestApp，跑 EXP-001/002 的同等检查 | 作为「真实安装」的基准对照组 |
