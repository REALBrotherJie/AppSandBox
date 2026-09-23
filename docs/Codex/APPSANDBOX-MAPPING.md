# AppSandbox 映射

这份映射说明如何把通用 UI 计划应用到当前 AppSandbox，不包含其他项目的类或代码。

| 通用任务 | AppSandbox 当前对应物 | 建议动作 |
|---|---|---|
| UI-00 | `MainActivity`、`activity_main.xml` | 记录首次进入、有效 APK、失败三张基线截图 |
| UI-01 | `values/colors.xml`、`values/themes.xml` | 新增语义颜色和 `values-night`，保留现有主题入口 |
| UI-02 | `MainActivity` 根布局 | 用 WindowInsets 处理状态栏、导航栏和键盘 |
| UI-03 | `guestSummary` | 将摘要改成卡片分组，并保留导入按钮 |
| UI-04 | `importUri` | 将复制、解析、展示拆成明确状态；防止重复导入 |
| UI-05 | `GuestPackageReader` 返回值 | 用纯数据模型渲染名称、包名、版本和组件统计 |
| UI-06 | `GuestStore` | 增加只读 Guest 记录列表接口，再接 RecyclerView |
| UI-07 | 当前 `ACTION_OPEN_DOCUMENT` | 增加 MIME、取消、无效文件和权限错误文案 |
| UI-08 | `errorText` | 用户文案与技术详情分层，避免直接展示异常原文 |

当前 AppSandbox 已经采用 Storage Access Framework 和私有 Guest 存储，这与 UI 计划中的文件导入入口一致。后续保持 `MainActivity -> GuestStore / GuestPackageReader` 的 clean-room 边界，不把 UI 逻辑写入解析或存储类。
