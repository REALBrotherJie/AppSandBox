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

## Round 11 Context Identity Recovery

Round 11 started at `c22757c8f3f573db14f8e49d7320d9407d139679`. QQ Browser and Baidu preserve Guest package/op-package through Application and derived Contexts, but both still stop when ConnectivityService checks the Host UID, which lacks physical `ACCESS_NETWORK_STATE`; no permission or connectivity behavior was changed in this Context-only round. SuperList exposed a separate contract defect: `Toast` sent the Guest op-package to `INotificationManager` while Binder carried the Host UID. The notification system boundary now translates exact Guest package arguments to the Host physical package without changing Guest-facing Context identity.

| App / API | I0 / I1 after Round 11 | First blocker after Round 11 |
| --- | --- | --- |
| QQ Browser / API31 | NO / NO | Host physical `ACCESS_NETWORK_STATE` capability missing |
| Baidu / API31 | NO / NO | Host physical `ACCESS_NETWORK_STATE` capability missing |
| SuperList / API31 | YES / YES | none through stable first frame |
| SuperList / API36 | YES / YES | none through stable first frame |
| Hide App List / API36 | NO / NO | unchanged obfuscated support-class `CLASSLOADER` failure |
| Damai Helper / API36 | YES / YES | none through stable first frame |

API31 improves from `3/18` to `4/18` independent Apps with dual-instance stable first frames: Momo, Kugou Lite, SuperList, and Damai Helper. API36 eligible improves from `1/3` to `2/3`; the 15 native ABI-ineligible Apps remain excluded. Momo API31 and API36 controls remain I0/I1 PASS. Termux API36 I0/I1 still log `guestCall=true guestTarget=28 before=0 after=2` before reaching the frozen PendingIntent client-compat blocker. Guest package, op-package, Application Context, derived configuration/device Context, storage, Provider Context, Binder translation, and Baidu remote-process routing were observed; Guest-facing `AttributionSource` and Virtual UID in Context-backed `ApplicationInfo` remain unproven. Raw evidence is under `build/reports/real-app-context-identity-11/`.

## Round 12 Host Capability Recovery

QQ Browser and Baidu both declare `ACCESS_NETWORK_STATE`; the Host manifest and installed Host package initially lacked it. A temporary debug build granted the Host normal permission, after which both Guest-with-permission and Guest-without-permission could call `getActiveNetwork()` and `getActiveNetworkInfo()`, proving `HOST_CAPABILITY_LEAK=YES`. The required minimal M6 gate was not safely injectable: API31's real `/apex/com.android.tethering/javalib/framework-connectivity.jar` contains `ConnectivityManager.mService`, but Java and JNI field/constructor access are blocked by the module's hidden-api boundary. No production permission or Connectivity behavior was left changed. QQ/Baidu remain at the same first blocker; API31 remains `4/18`, API36 eligible `2/3`. Evidence: `build/reports/real-app-host-capability-12/`.

## Round 13 System Service Mediation

API31 runtime probes proved `ConnectivityManager` is obtained by `SystemServiceRegistry` from the `connectivity` Binder, but its wrapper cache is application-context scoped: configuration, package, and device-protected derived Contexts still returned the Host-cached manager. Candidate A therefore cannot create a Guest-only manager without the already-rejected hidden constructor/field path. Candidate B's process-wide pre-wrapper Binder replacement and Candidate C's existing M6 interface proxy can identify the active M10 Guest generation, but cannot distinguish a Host Context call from a Guest Context call inside that physical slot; applying Guest manifest policy there would alter Host behavior. All three candidates were rejected and `SELECTED_SYSTEM_SERVICE_MEDIATION=NONE`; no production runtime or Host manifest permission was retained.

QQ Browser and Baidu still declare and hold Guest `ACCESS_NETWORK_STATE`, while the installed Host remains without it, so both remain at their previous first blocker. The conditional 18-App/API36 matrix was not rerun because production runtime did not change; counts remain API31 `4/18` and API36 eligible `2/3`. Momo and SuperList stayed first-frame PASS for I0/I1 on API31 and API36; Termux API36 I0/I1 retained Receiver translation `flags 0 -> 2`. No second shared Host runtime-permission capability was established from the current 18-App evidence; Baidu's later non-exported private provider denials are not a Host manifest capability. Evidence: `build/reports/real-app-system-service-mediation-13/`.

## Round 14 Next Generic Cluster Selection

After excluding the frozen PendingIntent, Connectivity capability, Chrome, WPS, ABI, and app-private boundaries, no eligible cluster reaches the generic-root-cause gate. `NATIVE_JNI` groups four different failures: Chromium's private `J.N.ZO`, a Zhihu initialization abort, a Flutter VM SIGSEGV, and Qunar's private `libgoblin` calling `FatalError`. `CLASSLOADER` groups three different missing-class owners: Douyin's OEM framework class, Lark's dependency interface, and Hide App List's obfuscated support class. The remaining Resource and Provider candidates each affect one App and do not yet prove an Android contract violation. Therefore `SELECTED_CLUSTER=NONE`, production runtime is unchanged, and the full matrix remains API31 `4/18`, API36 eligible `2/3` without rerun.

Fresh controls at Round 14 kept Momo and SuperList first-frame PASS for I0/I1 on API31 and API36, and Termux API36 I0/I1 retained Receiver translation `flags 0 -> 2`. The two frozen interception-dependent clusters remain Termux client PendingIntent and Guest-only Connectivity mediation, so `INTERCEPTION_SUBSTRATE_CLUSTER_COUNT=2`; the architecture-pivot threshold of three is not reached. `NEXT_ACTION=EXPAND_REAL_APP_SAMPLE`. Evidence: `build/reports/real-app-next-generic-cluster-14/`.

## Compatibility Expansion 2

From Round 14's unchanged runtime, 13 installed API31 third-party Apps were added: Damai, DingTalk, Arrow Out Grid Puzzle, Sudoku Cat Quest, EnhanceFox, Dragon Read, Duokan Reader, Lingqing Detector, Network Toolbox, PDF Editor, XM MusicPlayGo, Incy, and Kuaixun. Prescreen metadata (targetSdk 29-36, base/split count, ABI, and component counts) is under `build/reports/real-app-compatibility-expansion-2/metadata/selected-prescreen.csv`; raw two-instance logs are under `build/reports/real-app-compatibility-expansion-2/runtime/`.

API31 results: Lingqing Detector reached stable first frame in I0/I1; Kuaixun reached I0 only; the other new Apps stopped at their first recorded Application/Activity, dependency, licensing, network, or private-framework blocker. Three Dagger-like class failures were checked against APK contents: the reported classes were not defined as matching class descriptors (or were App-private dependency assumptions), so this is not a shared AppSandbox classloader defect. No new repeated generic blocker or third interception-substrate cluster was proven. API31 therefore moves from `4/18` to `5/31` independent Apps with dual-instance first-frame PASS.

The API36 emulator was unavailable after the API31 campaign, and none of the 13 new packages was installed on API36; the eligible denominator remains the prior 3 Apps and `2/3` PASS. Momo/SuperList controls remain PASS on both APIs and Termux Receiver remains `flags 0 -> 2`; no production runtime changed. `NEXT_GENERIC_BLOCKER=NONE`, `NEXT_ACTION=EXPAND_REAL_APP_SAMPLE`. Evidence: `build/reports/real-app-compatibility-expansion-2/`.

## Generic Runtime Correction 15

The Expansion-2 `NEW_REPEATED_GENERIC_BLOCKERS=NONE` classification was corrected from the second-pass evidence. This checkpoint added only Host `INTERNET`, Guest class-loader fallback after a failed Host lookup for non-`java.*` `android.*`/`javax.*` namespaces, and split-aware native materialization using Guest base/split APKs and reflected `primaryCpuAbi`. The API31 device was online; API36 `emulator-5554` was unavailable on 2026-10-03, so no new API36 eligible denominator was created. Build and unit gates passed. Full 31-App and fixture reruns were not completed in this session; prior baseline remains API31 `5/31`, API36 eligible `2/3`. Host INTERNET manifest enforcement is intentionally not Guest-manifest-faithful: `GUEST_INTERNET_OVERGRANT=NOT_TESTED`, `GUEST_INTERNET_MANIFEST_ENFORCEMENT=NOT_IMPLEMENTED`. EnhanceFox/Play Console native runtime acceptance remains unverified; no integrity bypass was attempted.
