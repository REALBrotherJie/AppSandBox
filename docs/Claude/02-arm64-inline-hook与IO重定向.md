# 02 arm64 inline hook 与 IO 重定向（阶段 5、6、7）

> 这是整个现代化里**最关键的枢纽**。在此之前，arm64 上的文件隔离/反检测全部是"看起来在做、实际没做"。

## 重大发现：arm64 上 IO 重定向一直是空操作

排查"给 app 报告干净 dataDir 反而破坏文件访问"时，连锁查出两个独立根因：

### 根因 1：`libva++.so` 从未落到磁盘

现代 Android 默认 `extractNativeLibs=false`，so 压在 apk 里**内存映射**，磁盘上没有独立文件路径。
而 native hook 引擎需要 so 的磁盘路径去 `dlopen`，于是一直报 `Unable to find libva++.so`，
IO 重定向从未被安装。

- **解决**：宿主 app 开启 `useLegacyPackaging = true`，让 so 解压到 `nativeLibraryDir`，
  从有磁盘路径的目录去 dlopen。
- 这是所有 native hook 能工作的**前置条件**，容易被忽略。

### 根因 2：inline hook 后端在 arm64 上根本没工作

即便 so 能加载，旧的 arm64 `MSHookFunction` 走的是 HookZz 的 `ZzHookReplace` 分支，
那条 inline hook 在 Android 12 / arm64 上**实际不生效**（早期只验证过"能编译链接"）。
于是 IOUniformer 对 libc `openat/mkdirat` 等的 hook 全是空操作——
**文件重定向、沙盒文件隔离在 arm64 上从未真正生效**。此前"一切正常"只是因为 VA 一直给 app 真实路径。

## 解决：自研一个小而自洽的 arm64 inline hook

替换失效的 HookZz（并从工程移除该依赖）。核心设计：

- **16 字节绝对跳转**改写目标函数序言：`LDR X17, #8` / `BR X17` / 8 字节目标地址。
- **trampoline 里重定位** PC 相对指令：只处理序言里常见的 `ADR` / `ADRP` / `LDR-literal`，
  把它们改写成"加载立即数/从绝对地址取值"的等价序列，然后跳回 `目标+16`。
- **遇到任何分支指令就放弃该 hook（安全退化）**：B/BL、B.cond、CBZ/CBNZ、TBZ/TBNZ、
  LDRSW-literal、PRFM-literal、SIMD&FP-literal 一律拒绝。原则是**"不能安全 hook 就原样保留，绝不损坏"**。
- `mprotect` 改目标页可写（RWX 失败则退回 RW）、`__builtin___clear_cache` 刷指令缓存。

要点：inline hook 的正确性上限就是"序言前 4 条指令能否安全重定位 + 遇到无法处理的一律退化"。
一个稳的退化路径比多支持几种指令更重要。

## 解决：hook 正确的 libc 符号名

旧代码 hook 的是 `__openat` / `__statfs` / `__getcwd` 这类**下划线内部符号**，
现代 arm64 bionic **不导出**它们，`dlsym` 全 miss，所以文件重定向从来没工作。修正为：

- hook **导出的** `openat` / `open` / `statfs` / `getcwd`。
- **`stat` 家族**：arm64 的 stat/lstat/fstatat 不走 `fstatat64`，要无条件 hook `stat`/`lstat`/`fstatat`
  （否则 `File.isDirectory` 等对干净路径判断错误）。
- **`opendir`**：bionic 的 `opendir` 内部走不可 dlsym 的 `__openat`，绕过 open/openat hook，
  导致 `File.listFiles()` 对干净路径返回 null（这正是酷安等应用崩的直接原因）。**必须直接 hook `opendir`**。
- **`__open_2`**：bionic 的 FORTIFY 包装是独立导出入口，两参数 `open()` 会直接调它，同样绕过 open/openat。
  也要 hook。

**教训**：现代 bionic 的 syscall 走法和导出符号与旧 Android 不同。
`arm64` 用通用 syscall ABI，不再有 legacy 32 位 `*64` 名字。要按当前 bionic 实际导出的符号来 hook，
并覆盖 FORTIFY 包装（`__open_2` 等）和内部走私有 syscall 的入口（`opendir`）。

## 外部存储重定向（阶段 7）

虚拟 app 写自己的 `Android/data/<pkg>` 外部目录会被 scoped storage 拒（EACCES）——
宿主 uid 无权写"别的包"的外部私有目录。

- **方案**：把各 sdcard 别名（`/storage/emulated/0`、`/sdcard`、`/mnt/sdcard`、`/storage/self/primary`）下的
  `Android/{data,obb,media}/<pkg>` 重定向到**虚拟内部数据目录的 `ext_storage/` 子目录**（必然可写）；
  共享媒体（DCIM/Download）保持真实路径。
- 局限：若 app 严格校验"路径是否真在 sdcard 上"（极少见），需改为重定向到宿主真实外部目录。

## 给 AppSandBox 的启示（若将来进入运行时阶段）

- native hook **必须先在目标 ABI 上真跑通并验证**，不能只验证编译链接。做一个"能安全退化"的最小实现胜过引入大而不验的库。
- `extractNativeLibs=false` 是现代默认，任何依赖 so 磁盘路径的机制都要先解决 so 落盘问题。
- hook libc 前，先在目标设备上 `dlsym` 确认符号确实导出；覆盖 FORTIFY 包装与内部私有 syscall 入口。
- 文件隔离一旦"真正生效"，反而会让**依赖真实路径的加固 app 崩溃**（见 [08](./08-复杂应用抽测结论.md)）——
  这是正确性带来的新问题，要有预期。
