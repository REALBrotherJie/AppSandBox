# Activity Runtime Research

状态：DESIGN RESEARCH，整理日期：2026-09-24。

本目录研究 API31/API36 Activity 启动链、system_server/client 边界、Activity virtualization 路线及版本风险。结果服务于架构决策，不代表实现承诺。

- `01_ANDROID12_ACTIVITY_LAUNCH_CHAIN.md`: API31 AOSP 链路。
- `02_ANDROID16_ACTIVITY_LAUNCH_CHAIN.md`: API36 对比。
- `03_ACTIVITY_REQUIRED_STATE.md`: Activity 所需状态及所有权。
- `SOURCES.md`: 来源登记。

优先使用 AOSP 和 Android Developers。公开框架项目只提取架构模式；2015-2019 资料不能直接标记为 Android 12-16 当前实现。
