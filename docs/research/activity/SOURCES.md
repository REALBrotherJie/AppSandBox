# Activity Research Sources

访问/整理日期：2026-09-24。AOSP/Android Developers 为 primary；项目和论文只用于架构模式与风险背景，不复制源码。

| ID | Source | Version/date | Type | Relevance |
|---|---|---|---|---|
| A01 | https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/ActivityThread.java | API31 | PRIMARY AOSP | client launch |
| A02 | https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/ContextImpl.java | API31 | PRIMARY AOSP | activity context |
| A03 | https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/Activity.java | API31 | PRIMARY AOSP | attach |
| A04 | https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/Instrumentation.java | API31 | PRIMARY AOSP | start/newActivity |
| A05 | https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/services/core/java/com/android/server/wm/ActivityTaskManagerService.java | API31 | PRIMARY AOSP | system entry |
| A06 | https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/services/core/java/com/android/server/wm/ActivityStarter.java | API31 | PRIMARY AOSP | resolution/task policy |
| A07 | https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/services/core/java/com/android/server/wm/ActivityRecord.java | API31 | PRIMARY AOSP | record/token |
| A08 | https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/servertransaction/LaunchActivityItem.java | API31 | PRIMARY AOSP | transaction |
| A09 | https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/app/ActivityThread.java | API36 | PRIMARY AOSP | current client path |
| A10 | https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/app/ContextImpl.java | API36 | PRIMARY AOSP | current context path |
| A11 | https://developer.android.com/guide/components/activities/tasks-and-back-stack | current | PRIMARY official | task/affinity/launchMode |
| A12 | https://developer.android.com/guide/components/activities/activity-lifecycle | current | PRIMARY official | lifecycle/process |
| A13 | https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture | current | PRIMARY official | predictive back |
| A14 | https://developer.android.com/reference/android/app/AppComponentFactory | API28+ | PRIMARY official | public instantiation boundary |
| A15 | https://developer.android.com/reference/android/app/Instrumentation | current | PRIMARY official | public instrumentation |
| A16 | https://developer.android.com/reference/android/app/Activity | current | PRIMARY official | public Activity contract |
| A17 | https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces | current | PRIMARY official | hidden API risk |
| A18 | https://github.com/asLody/VirtualApp | 2017-origin | PROJECT reference | stub/proxy pattern; age warning |
| A19 | https://github.com/Qihoo360/RePlugin | 2016-origin | PROJECT reference | plugin stub pattern |
| A20 | https://github.com/Tencent/Shadow | 2018-origin | PROJECT reference | loader/resource separation |
| A21 | https://github.com/didi/virtualapk | 2017-origin | PROJECT reference | manifest substitution |
| A22 | https://github.com/Qihoo360/DroidPlugin | 2015-origin | PROJECT reference | service proxy history |
| A23 | https://github.com/android-hacker/VirtualXposed/wiki/How-does-VirtualXposed-work | 2019 | PROJECT reference | client hook pattern |
| A24 | https://www.usenix.org/system/files/conference/usenixsecurity15/sec15-paper-backes.pdf | 2015 | PAPER | virtualization security |
| A25 | https://www.cs.ucr.edu/~zhiyunq/pub/sigmetrics19_app_virtualization.pdf | 2019 | PAPER | identity/threat model |

Older project material is not CURRENT evidence for API31-36. API36 behavior must be confirmed against current AOSP and devices before implementation.
