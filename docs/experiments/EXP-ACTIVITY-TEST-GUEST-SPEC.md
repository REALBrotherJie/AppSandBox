# Controlled Guest Activity Test Specification

状态：FUTURE TEST INPUT ONLY，不创建、不执行。

未来 APK 仅包含 `GuestMainActivity extends Activity`、一个 resource layout、一个 Guest theme 和可见 TextView marker。Activity 记录 lifecycle callbacks，读取一个 primitive Intent extra，显示 orientation/configuration marker；不使用 WebView、JNI/native、PackageManager、network 或 runtime permission。

Manifest baseline：一个 explicit entry point、`exported` 明确、standard launchMode、固定 theme/orientation、无 service/provider/receiver/split Activity。

Success observations：

- Host Stub 获得真实 system token/window。
- Guest class 从 immutable Guest revision 实例化。
- Guest resources/theme/layout 在 content creation 前生效。
- 每个 lifecycle callback 可归属于唯一 instance，顺序可解释。
- primitive Intent extra 经 translation 保留，system_server 不解析 Guest Parcelable。
- Host/system identity 与 logical Guest identity 分开记录。

Negative controls：直接启动未安装 Guest component、Host Activity + Guest View、错误/可写 APK、同 component 双实例、mapping 持久化前后 process death。
