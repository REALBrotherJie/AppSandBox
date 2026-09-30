# M1-FIX：恢复实例存储的风险测试

日期：2026-09-30
状态：待执行
前置阅读：`AGENTS.md`、`docs/Claude/Task/M1-RESULT.md`

## 大目标

M1（`ce39b2b`）删除死路代码时，把保留模块 `GuestInstanceStore` 的风险测试 `app/src/test/java/com/example/appsandbox/storage/GuestInstanceStoreTest.kt`（551 行）整个删除了。该模块是后续多开的实例注册表，必须恢复其与逻辑 Activity 无关的测试。

## 验收标准

1. 从 `ce39b2b^` 取回 `GuestInstanceStoreTest.kt`，**只删除**依赖已移除逻辑 Activity 的用例：
   - `activeLogicalActivityRejectsDeleteAndClosedAllowsIsolatedDelete`
   - `deleteWaitsForBeginCommitThenRejectsOpen`
   - `corruptLogicalActivityStateRefusesDeleteWithoutChangingRegistry`
   - `beginWaitsForDeleteThenCannotResurrectDirectory`
   - `foreignProcessLockBlocksBeginAndDelete`（若只测试逻辑 Activity 锁则删除；若同时覆盖注册表锁，改写为只测注册表锁）
2. 其余用例全部保留并通过，至少包括：
   - create/list/get/delete 基本流程，canonical 路径别名；
   - 写入失败时回滚目录、保留旧注册表；
   - 注册表损坏时 create/list/get fail closed；
   - 非法 ID、外部 dataRoot、符号链接与悬空符号链接逃逸被拒绝；
   - 并发不同 ID、同 ID 竞争、并发 create/delete 不丢更新；
   - 删除写入失败与目录删除失败后的恢复与重试；
   - 外部注册表事务阻塞 Store 修改。
3. 若恢复的测试暴露了 M1 对 `GuestInstanceStore` 的改动引入的回归（例如 create 时目录已存在的并发检查），修复生产代码，不削弱测试。
4. `docs/Claude/Task/M1-RESULT.md` 中 `commit = pending` 改为实际 commit，并追加一行说明本次修正。
5. `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:assembleRelease` 通过。

## 硬边界

- 只改动上述测试文件、`GuestInstanceStore` 相关生产代码（仅在第 3 条需要时）和 `M1-RESULT.md`。
- 不涉及设备验证，不开始 M2 内容。
- focused commit，不 push。

## 最终回传

```text
status =
restored tests (count) =
removed logical-activity tests =
production fixes =
build/tests =
commit =
pushed = false
```
