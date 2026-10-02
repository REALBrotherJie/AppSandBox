# Real App Compatibility Matrix V2

Audit date: 2026-10-02. Start/current runtime: `42e4ee1e70fb96f539a4ae59581ba3a0a609c2d2`. API31 is arm64-v8a device `7b670025`; API36 is x86_64 emulator `emulator-5554`. This expands the [original seven-App audit](REAL-APP-COMPATIBILITY-AUDIT.md) without changing production runtime. Raw metadata, APK inventory, and I0/I1 logs are under `build/reports/real-app-expansion/`.

## Eligibility And First Blocker

`YES` in an instance column means an AppSandbox Guest first-frame timestamp was observed. `NO` stops at the listed blocker. API36 excludes every App whose installed APK contains native code but no x86_64 payload, even if emulator translation happened to work in an earlier campaign.

| App (package, version) | targetSdk | APK / native ABI | API31 | API36 x86_64 | API31 I0 / I1 | API36 I0 / I1 | first blocker on primary API31 | category |
| --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| Momo SDK sample (`com.reel.mylibrary`, 1.0) | 35 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | YES / YES | control PASS / PASS, excluded | none observed; core interaction previously verified | none |
| Chrome (`com.android.chrome`, 154.0.8037.92) | 36 | base+splits / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | Chromium `J.N.ZO`; generic defect unproven | `NATIVE_JNI` |
| Termux (`com.termux`, 0.118.3) | 28 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | control only, excluded | PendingIntent mutability client compat leakage, frozen | `PENDINGINTENT` |
| Coolapk (`com.coolapk.market`, 15.9.1) | 33 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | provider/Application bootstrap does not reach Activity | `PROVIDER_BINDER` |
| Zhihu (`com.zhihu.android`, 10.80.0) | 34 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | ART abort during native initialization | `NATIVE_JNI` |
| WPS (`cn.wps.moffice_eng`, 14.37.0) | 34 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | RePlugin private `BinderCursor` assumption | `APP_PRIVATE_FRAMEWORK_ASSUMPTION` |
| QQ Browser (`com.tencent.mtt`, 19.7.2.2073) | 30 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | Host lacks physical `ACCESS_NETWORK_STATE` capability | `CONTEXT_IDENTITY` |
| iQIYI (`com.qiyi.video`, 16.12.2) | 35 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | `QYControlButton` inflate: Fresco `SimpleDraweeView` not initialized | `RESOURCE` |
| Kugou Lite (`com.kugou.android.lite`, 5.1.3) | 30 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | YES / YES | excluded | none through stable first frame; core interaction not observed | none |
| Douyin Lite (`com.ss.android.ugc.aweme.lite`, 40.0.0) | 34 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | Application attach cannot resolve OEM `android.view.OdViewStub` | `CLASSLOADER` |
| WeChat (`com.tencent.mm`, 8.0.66) | 34 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | Tinker `baseRevision(...) must not be null` | `APP_PRIVATE_FRAMEWORK_ASSUMPTION` |
| Baidu (`com.baidu.searchbox`, 15.73.0.10) | 35 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | ConnectivityService rejects Host UID without Guest `ACCESS_NETWORK_STATE` | `CONTEXT_IDENTITY` |
| Lark (`com.ss.android.lark`, 7.74.9) | 35 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | Application bootstrap cannot resolve `javax.inject.Provider` | `CLASSLOADER` |
| Google Play Console (`com.google.android.apps.playconsole`, 9.0.939617269) | 37 | base+3 splits / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | Flutter cannot bootstrap VM data, then SIGSEGV | `NATIVE_JNI` |
| Qunar (`com.Qunar`, 10.4.8) | 34 | base / arm64 | eligible | `NOT_ELIGIBLE_NATIVE_ABI` | NO / NO | excluded | private `libgoblin_6_1_1.so` calls JNI FatalError in `JNI_OnLoad` | `NATIVE_JNI` |
| SuperList (`com.topmiaohan.superlist`, 1.0) | 34 | base / none | eligible | eligible | NO / NO | NO / NO | `INotificationManager.enqueueTextToast`: Guest package not owned by Host UID | `CONTEXT_IDENTITY` |
| Hide App List (`com.topmiaohan.hidebllist`, 1.5) | 31 | base / none | eligible | eligible | NO / NO | NO / NO | obfuscated `android.support.v4.os.*` class absent in Host loader | `CLASSLOADER` |
| Damai Helper (`com.rookie.damaihelper`, 1.0.12) | 31 | base / none | eligible | eligible | YES / YES | YES / YES | none through stable first frame; core interaction not observed | none |

## Prescreen Coverage

The 11 new Apps cover targetSdk 30-37, single APK and split APK, pure Java and native-heavy packages, 0-58 declared processes, 1-62 providers, 0-71 receivers, and 0-244 services. Play Console has base plus arm64, locale, and density splits. The other ten are single-APK installs. Full counts are in `build/reports/real-app-expansion/metadata/new-app-prescreen.csv`.

## Compatibility Funnel And Tiers

All 18 packages passed installed-package import and package parse on API31. Only Momo has previously verified core interaction, so it is Tier A. Kugou Lite and Damai Helper are Tier B because both instances reached stable first frame while core interaction is `NOT_OBSERVED`. Thirteen Apps are Tier C because Guest Application or Activity execution began but no stable first frame was produced. Lark and Hide App List are Tier D because class resolution prevents usable Guest startup. Process restart, remote process, WebView, and background components remain `NOT_OBSERVED` unless the historical audit explicitly records them.

| Metric | Result |
| --- | ---: |
| total independent Apps | 18 |
| API31 eligible / stable first-frame pass | 18 / 3 |
| API36 x86_64 eligible / stable first-frame pass | 3 / 1 |
| Tier A / B / C / D | 1 / 2 / 13 / 2 |
| API36 environment-incompatible native Apps | 15 |

## Blocker Clusters

| Blocker category | Independent Apps affected | API | Generic evidence |
| --- | --- | --- | --- |
| `CONTEXT_IDENTITY` | QQ Browser, Baidu, SuperList | 31; SuperList also 36 | Three unrelated Apps cross system_server with Guest package semantics but Host UID/capability. SuperList is an explicit Android package/UID ownership contract violation; QQ Browser and Baidu independently hit the same physical-capability boundary in ConnectivityService. |
| `NATIVE_JNI` | Chrome, Zhihu, Play Console, Qunar | 31 | Repeated category, but failures occur in different private runtimes and do not yet prove one shared AppSandbox boundary. |
| `CLASSLOADER` | Douyin Lite, Lark, Hide App List | 31; Hide also 36 | Different missing classes and packaging assumptions; no common contract violation proven. |
| `APP_PRIVATE_FRAMEWORK_ASSUMPTION` | WPS, WeChat | 31 | Independent private plugin/patch frameworks, not a generic recovery target. |

`CLIENT_COMPAT_CLUSTER_CONFIRMED = NO`: no new independent App reproduced Termux's pre-Binder PendingIntent targetSdk leakage. `NEXT_GENERIC_BLOCKER = CONTEXT_IDENTITY` with three independent Apps. This is a recommendation input only; M12 remains frozen and no Recovery Round is created here.

## Controls And Build

Momo API31/API36 I0/I1 each emitted a Guest first-frame timestamp. Termux API36 I0/I1 each logged `guestCall=true guestTarget=28 before=0 after=2`; the next blocker remains the frozen PendingIntent path. `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleRelease` passed at the unchanged runtime HEAD.
