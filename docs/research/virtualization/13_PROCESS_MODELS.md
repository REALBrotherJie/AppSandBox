# 13 Process Models

状态标签：`CPS`=CONFIRMED BY PRIMARY SOURCE，`PC`=PROJECT CLAIM，`SSS`=SINGLE SECONDARY SOURCE，`SPEC`=SPECULATIVE，`NE`=NEEDS EXPERIMENT。来源编号见 `00_SOURCES.md`。

## 1. 可选模型

| 模型 | 说明 | 先例 |
|---|---|---|
| P1 Host 单进程 | Host 与所有 Guest 同一个进程 | EXP-001/002 的现状 |
| P2 共享沙箱进程 | 所有 Guest 共用一个与 Host 分开的进程 | — |
| P3 固定进程槽位 | Host manifest 预先声明 N 个 `android:process`，Guest 运行时分配到槽位 | VirtualApp 描述的多类进程（Host Main / Plugin / Client / Server）`[S01][S08]` PC |
| P4 每个包一个进程 | 从槽位里为每个 Guest 包分配一个 | 同上 |
| P5 每个实例一个进程 | 同一个包的多个实例分别占用槽位 | 同上 |
| P6 isolatedProcess | 以 `android:isolatedProcess="true"` 的 Service 运行，**每个隔离进程有独立 UID** | Chrome 渲染进程 `[S69]`、Firefox 标签页进程 `[S70]` |

## 2. 关键约束

- **普通应用不能在运行时新增进程名**：所有进程都要在 Host manifest 里预先声明。这就是「固定槽位」模型的由来。CPS（Android manifest 机制）
- **非隔离进程共用 Host UID**：P1–P5 的所有进程都是同一个 Linux UID，文件系统和权限层面没有 OS 级隔离（ADR-0002）。进程分离只带来**崩溃隔离、静态状态隔离和 native 全局状态隔离**。
- **isolatedProcess 的 UID 是独立的**，但限制非常多 `[S69][S70]` CPS（厂商文档）：
  - 没有应用的运行时权限，也不能访问应用的私有文件；
  - 不能启动或绑定大部分组件，大多数系统服务不可用；
  - SELinux 限制更严；
  - 需要通过 IPC 请求非隔离进程代为执行需要权限的操作；
  - `bindIsolatedService`（API 29）可以为同一个 Service 声明创建多个隔离实例；App Zygote（`useAppZygote`，API 29）可以预加载代码，加快隔离进程的启动 `[S70][S73]`。
- 学术先例：Boxify 用「应用虚拟化 + 基于进程的权限分离」来封装不受信任的应用，不需要 root，也不需要修改固件 `[S43]`（摘要原文）。它是否具体用了 isolatedProcess，需要读全文确认，NE。

## 3. 各模型对比

| 维度 | P1 单进程 | P3–P5 槽位 | P6 isolatedProcess |
|---|---|---|---|
| 同名类 / 静态状态 | 靠不同 ClassLoader 隔离（EXP-001 已证明） | 进程级隔离 | 进程级隔离 |
| JNI 全局状态、同名 .so | 同一进程内会冲突 | 隔离 | 隔离 |
| 崩溃影响 | Guest 崩溃会带崩 Host | 只影响该槽位 | 只影响该进程 |
| 内存 | 最省 | 每个进程都要付 ART 和框架的开销 | App Zygote 可以缓解 |
| `processName` | Host 的名字 | 槽位名 | 隔离进程名 |
| 系统服务（UI、通知、定位等） | 可用（以 Host 身份） | 可用（以 Host 身份） | **基本不可用**，必须经 Broker 转发 |
| OS 级隔离（UID / 文件 / 权限） | 无 | 无 | **有** |
| 适合承载 | 实验阶段 | 完整的 Guest 应用 | 不需要 UI 和系统服务的计算型 Guest 代码，或作为「需要强隔离的部分」 |

## 4. 对 AppSandbox 的建议（PROPOSED，不实现）

1. EXP-003 阶段继续使用 P1，但要把「Guest 崩溃会带崩 Host」明确列为已知限制。
2. 进入组件阶段后，默认采用 P3（固定槽位），槽位数量和进程名写进 Host manifest，由 Process Runtime 管理分配。
3. P6 可以作为**安全增强方向**单独研究：它是普通应用唯一能获得的「每个进程独立 UID」的公开机制，但它不能直接承载有界面的完整 Guest。先做一个小实验，确认哪些 Guest 能力可以在隔离进程里运行。

## 5. 建议实验（不执行）

| 编号 | 内容 | 观测 |
|---|---|---|
| PR-1 | 在 isolatedProcess Service 里用 DexClassLoader 加载 GuestTestApp 的 `GuestProbe`（APK 通过 Broker 以只读文件描述符传入） | 能否加载；隔离进程能否读到 Host 私有目录（预期不能） |
| PR-2 | 同一个 isolatedProcess 里尝试 Option B 资源加载 | 记录哪一步失败 |
| PR-3 | 两个槽位进程分别加载同一个 Guest | 静态状态与 native 全局状态是否互不影响 |
