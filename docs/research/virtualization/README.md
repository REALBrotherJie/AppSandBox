# Virtualization Research Index

依据：`docs/ChatGPT/web-search-01.txt`。本目录只存放外部调研结果，不授权任何实现，也不修改 ADR（建议改动一律标为 ADR REVIEW CANDIDATE）。

## 已完成（2026-09-23）

| 文件 | 内容 | 对后续工作的直接用途 |
|---|---|---|
| `00_SOURCES.md` | 来源登记表（S01–S65），含可信度与访问状态 | 所有文档都用 `[Sxx]` 引用；需要人工补读的来源列在 E 节 |
| `07_APPLICATION_LOADEDAPK.md` | Application / ContextImpl / LoadedApk 的关系 | EXP-003B 的前置条件和泄漏项 |
| `08_RESOURCES_STRATEGIES.md` | 各资源方案对比；Option B 的先例与待补细节 | 修订 ADR-0006；实验 R-1~R-5 |
| `16_ANDROID_VERSION_CHANGES.md` | Android 12→16 中与项目相关的变化；Top 10 风险 | 实验 V-1~V-3（V-1 优先级最高） |
| `19_WORK_PROFILE_ALTERNATIVE.md` | Work Profile 与应用级虚拟化对比 | 产品路线决策；实验 W-1~W-3 |
| `13_PROCESS_MODELS.md` | 进程模型对比；isolatedProcess 的独立 UID 与限制 | 实验 PR-1~PR-3 |
| `23_SPLIT_APK_SUPPORT.md` | split APK 的影响与公开 API 支撑 | 实验 SP-1~SP-3 |
| `24_SIGNING_AND_PACKAGE_IDENTITY.md` | 用公开 API 获取并校验未安装 APK 的签名 | 实验 SG-1~SG-3 |
| `26_GAPS_IN_CURRENT_APPSANDBOX.md` | 已覆盖 / 缺失 / 低估 / 可能错误 / 新方向 | 负责人的审查清单；ADR REVIEW CANDIDATES |

## 尚未编写

01–06、09–12、14、15、17、18、20–22、25，以及总报告 `docs/research/ANDROID_APP_VIRTUALIZATION_2026_MASTER_SURVEY.md`。

## 建议的实验优先级（汇总，不执行）

1. **V-1**：在 API 34+ 上验证 Guest APK 是否必须只读（可能直接影响 EXP-001 的结论）
2. **R-1 / R-2**：Option B 的配置变体，以及在全新进程中做隔离检查（修订 ADR-0006 的依据）
3. **L-1 / L-2**：Guest Application 的 Context 链和 `mLoadedApk` 观测（EXP-003B 的前置）
4. **R-3 / V-3**：Option B 在 API 28/29 和 35/36 上的表现
5. **W-1 / W-3**：Work Profile 基准对照
