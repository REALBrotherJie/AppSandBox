# AGENTS.md — AppSandBox 执行会话规则

所有 Codex 会话开始时必须先读本文件。本文件优先于仓库内其他所有流程文档。

## 1. 最终目标（不可修改）

在 Google Play 上架一个**真·多开/分身** App：用户可以对**手机上已安装的真实 App** 创建一个或多个分身，分身能正常运行、数据相互隔离、可以长期稳定使用。

判断任何工作是否有价值，只问一个问题：**它让真实 App 在分身里跑得更好了吗？**

## 2. 权威文档与层级

| 层级 | 位置 | 谁可以修改 |
|---|---|---|
| 目标与总体规划 | `docs/Claude/Task/01-方向A总体规划.md` | 仅规划者（用户/Claude） |
| 当前任务 | `docs/Claude/Task/M<N>-*.md` | 仅规划者 |
| 任务结果 | `docs/Claude/Task/M<N>-RESULT.md` | 执行会话 |
| 边界决策 | `docs/adr/ADR-0013-DIRECTION-A.md` 及之后的 ADR | 执行会话按任务要求编写 |

**以下文档已作废，仅作历史参考，不得作为约束或验收依据：**
`docs/Codex/00_SESSION_REQUIREMENTS.md`、`docs/Codex/task-*.txt`、`docs/ChatGPT/task-*.txt`、`docs/ROADMAP.md`、`docs/design/24_IMPLEMENTATION_ROADMAP.md`，以及其中"禁止 hook / hidden API / Binder 代理 / attach"的条款。旧 `docs/design/`、`docs/review/`、`docs/experiments/` 可以查阅机制分析，但与本文件冲突时以本文件为准。

## 3. 执行会话的职责与禁止事项

执行会话**只执行用户指定的那一个 `M<N>` 文件**，并连续完成：实现 → 风险测试 → API31/API36 设备验证 → 自行修复 → 写 RESULT → focused commit。

必须遵守：

- **不得**修改目标、里程碑划分或验收标准；**不得**自行创建新 task 或把工作拆成后续 task。
- **不得**用文档、矩阵、证据文件代替验收；验收以任务文件列出的真实行为为准。
- 发现验收标准不合理、做不到、或与本文件冲突时：**停止并在回传中说明**，由规划者修订任务，不要自行变更方向。
- 同一目标内可以自行处理构建失败、设备重试、测试补充和不改变架构的实现调整，不需要中途询问。

只有以下情况可以提前结束：需要用户提供账号、密钥、设备、包名等外部输入；同一技术阻塞经多次合理尝试仍无法突破；继续操作会越过第 4 节的禁止项。

## 4. 技术边界

**允许：** hidden API 访问、系统服务 Binder 代理、stub 组件与 stub 进程池、Instrumentation/ActivityThread/ClientTransaction 介入、arm64 native inline hook 与 IO 重定向。

**禁止：**

- 复制 VirtualApp、BlackBox、DroidPlugin 等项目的源码或照搬其结构；`docs/Claude/01–09` 只作为问题与根因参考，实现须独立编写。
- 反作弊绕过、Play Integrity/SafetyNet 对抗、支付/银行类安全机制绕过、设备身份伪造。
- release 包执行来自非已安装包（例如导入的 APK 文件）的代码；APK 文件导入只允许存在于 debug 包。
- 申请 `QUERY_ALL_PACKAGES`；列出可分身 App 使用 `<queries>` 声明 MAIN/LAUNCHER intent。

## 5. 工作约定

- 测试设备：API31 真机 `7b670025`，API36 模拟器 `emulator-5554`。设备离线先尝试恢复，不是结束理由。
- 基础构建检查：`:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:assembleRelease`。
- 原始设备输出（logcat、dumpsys、截图）只写入 `build/reports/m<N>/`，不入库。
- 测试投入按风险排序：虚拟 PMS/AMS 状态、进程死亡恢复、路径重定向、多实例隔离优先；不写只验证 getter/构造的测试。
- 每个里程碑一个或少量 focused commit；**不 push**，除非用户明确要求；保留用户无关的工作区改动。
- RESULT 文档 ≤ 20 行：真实 App 清单与各自结果、未完成项、已知限制、commit。
- 最终回传使用任务文件末尾给出的格式，不要附加长篇总结。
