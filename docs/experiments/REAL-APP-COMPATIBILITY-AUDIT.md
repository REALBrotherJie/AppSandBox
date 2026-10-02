# Real App Compatibility Audit

Audit anchor: `57c31b41aec3ad00feb58c341e1152e5c6d86185`. API31 device: `7b670025` (arm64-v8a). API36 device: `emulator-5554` (x86_64). Seven unchanged third-party APKs were copied from the installed API31 packages to API36 when needed. Each package was launched through AppSandbox with independent `Instance0` and `Instance1`; testing stopped at the first blocker.

| App | API | I0 first frame | I1 first frame | restart | multiprocess | native | WebView | first blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Momo SDK sample (`com.reel.mylibrary`) | 31 | BLOCKED after Activity RESUME | BLOCKED after Activity RESUME | NOT_TESTED | NOT_OBSERVED | native path initialized | NOT_OBSERVED | WorkManager DB path is double-prefixed under virtual data root; `SQLITE_CANTOPEN` |
| Momo SDK sample (`com.reel.mylibrary`) | 36 | BLOCKED after Activity RESUME | BLOCKED after Activity RESUME | NOT_TESTED | NOT_OBSERVED | arm64 translation reached Activity | NOT_OBSERVED | same WorkManager DB double-prefix and `SQLITE_CANTOPEN` |
| Chrome (`com.android.chrome`) | 31 | FAIL before first frame | FAIL before first frame | NOT_TESTED | NOT_OBSERVED | FAIL | NOT_TESTED | Chromium JNI method `J.N.ZO` has no native implementation loaded |
| Chrome (`com.android.chrome`) | 36 | FAIL before first frame | FAIL before first frame | NOT_TESTED | NOT_OBSERVED | arm64 translated | NOT_TESTED | Chromium async initialization dereferences null Context |
| Termux (`com.termux`) | 31 | BLOCKED after Activity RESUME | BLOCKED after Activity RESUME | NOT_TESTED | NOT_OBSERVED | native/path rewrite observed | NOT_OBSERVED | TermuxService creates PendingIntent without required mutability flag |
| Termux (`com.termux`) | 36 | FAIL before stable first frame | FAIL before stable first frame | NOT_TESTED | NOT_OBSERVED | arm64 translated | NOT_OBSERVED | dynamic receiver registration lacks API36 exported/not-exported flag |
| Coolapk (`com.coolapk.market`) | 31 | BLOCKED during Application/providers | BLOCKED during Application/providers | NOT_TESTED | NOT_OBSERVED | native setup reached | NOT_OBSERVED | startup stalls while installing large provider set; no stable Activity/first frame |
| Coolapk (`com.coolapk.market`) | 36 | BLOCKED during Application/native setup | BLOCKED during Application/native setup | NOT_TESTED | NOT_OBSERVED | arm64-only payload | NOT_OBSERVED | arm64-only APK on x86_64 plus startup never reaches stable Activity |
| Zhihu (`com.zhihu.android`) | 31 | BLOCKED during Application/providers | BLOCKED during Application/providers | NOT_TESTED | NOT_OBSERVED | native setup reached | NOT_OBSERVED | startup does not reach stable Activity after large provider initialization |
| Zhihu (`com.zhihu.android`) | 36 | FAIL before first frame | FAIL before first frame | NOT_TESTED | NOT_OBSERVED | FAIL | NOT_OBSERVED | `libDexHelper.so` is AArch64, not x86_64 (`GUEST_APP_PLATFORM_INCOMPATIBILITY`) |
| WPS (`cn.wps.moffice_eng`) | 31 | FAIL before first frame | FAIL before first frame | NOT_TESTED | NOT_OBSERVED | native setup reached | NOT_OBSERVED | non-exported RePlugin ProcessPitProvider opened using Host UID |
| WPS (`cn.wps.moffice_eng`) | 36 | FAIL before first frame | FAIL before first frame | NOT_TESTED | NOT_OBSERVED | FAIL | NOT_OBSERVED | arm64 `libcp-lib.so` incompatible, then same non-exported provider denial |
| QQ Browser (`com.tencent.mtt`) | 31 | FAIL before first frame | FAIL before first frame | NOT_TESTED | NOT_OBSERVED | native setup reached | NOT_TESTED | Host lacks Guest `ACCESS_NETWORK_STATE`; ConnectivityService SecurityException |
| QQ Browser (`com.tencent.mtt`) | 36 | FAIL at instance creation | FAIL at instance creation | NOT_TESTED | NOT_OBSERVED | NOT_TESTED | NOT_TESTED | virtual package not registered on current API36 visibility inventory |

## Blockers

| Blocker | Apps affected | Milestone owner | Severity |
| --- | --- | --- | --- |
| Guest data/database path double-prefix | Momo | M5/M9 | BLOCKS_CORE_FLOW |
| Native/JNI loading or ABI mismatch | Chrome, Zhihu, WPS, Coolapk | M9 / Guest platform compatibility | BLOCKS_STARTUP |
| Provider physical UID/exported boundary | WPS | M6/M8 | BLOCKS_STARTUP |
| Guest permission not represented by Host physical capability | QQ Browser | M6/M12 permission boundary | BLOCKS_STARTUP |
| Dynamic Receiver API-level contract | Termux API36 | M8 | BLOCKS_CORE_FLOW |
| PendingIntent API31 contract | Termux API31 | Guest compatibility/M12 interception | BLOCKS_CORE_FLOW |
| Virtual package visibility/registration | QQ Browser API36 | M2/M3 | BLOCKS_STARTUP |
| Complex provider startup does not complete | Coolapk, Zhihu API31 | M2/M8 | BLOCKS_STARTUP |

## Decision Input

No app reached Notification, Alarm, or Job as its first AppSandbox-specific blocker. Most failed in M2-M10 startup, storage, native, provider, permission, or receiver boundaries. `RECOMMEND_RESUME_M12 = NO`. `NATIVE_REAL_APP_GAP` is present: M9 initializes for Momo, but Momo first fails on duplicated virtual DB path; Chrome separately exposes missing JNI registration. Raw evidence is under `build/reports/real-app-audit/`.

## Recovery Round 1

| App | API | After Recovery Round 1 |
| --- | --- | --- |
| Momo SDK sample (`com.reel.mylibrary`) | 31 | I0/I1 PASS: stable interactive first frame; next issue is non-fatal Host physical `INTERNET` capability |
| Momo SDK sample (`com.reel.mylibrary`) | 36 | I0/I1 PASS: stable interactive first frame; no `SQLITE_CANTOPEN` |
| Chrome (`com.android.chrome`) | 31 | unchanged: I0/I1 fail at `J.N.ZO` JNI |
| Chrome (`com.android.chrome`) | 36 | unchanged: I0/I1 null Context, then `J.N.ZO` JNI |
| Termux (`com.termux`) | 31 | unchanged: I0/I1 reach RESUME, then PendingIntent mutability exception |
| Termux (`com.termux`) | 36 | unchanged: I0/I1 fail dynamic Receiver exported flag contract |
| Coolapk (`com.coolapk.market`) | 31 | unchanged: I0/I1 stall during large provider bootstrap |
| Coolapk (`com.coolapk.market`) | 36 | unchanged: I0/I1 repeat provider/native startup without stable frame |
| Zhihu (`com.zhihu.android`) | 31 | I0/I1 reach native initialization, then ART `SIGABRT` before frame |
| Zhihu (`com.zhihu.android`) | 36 | unchanged: I0/I1 fail AArch64 `libDexHelper.so` on x86_64 |
| WPS (`cn.wps.moffice_eng`) | 31 | provider denial fixed; new blocker is null RePlugin `BinderCursor.BinderParcelable` |
| WPS (`cn.wps.moffice_eng`) | 36 | self-provider route succeeds; first blocker remains arm64 `libcp-lib.so` on x86_64 |
| QQ Browser (`com.tencent.mtt`) | 31 | unchanged: no stable frame; physical network capability boundary |
| QQ Browser (`com.tencent.mtt`) | 36 | unchanged: virtual package not registered |

Evidence is under `build/reports/real-app-recovery-1/`. `BASELINE_FIRST_FRAME = 0/7`; `AFTER_FIX_FIRST_FRAME = 1/7`; `FIRST_BLOCKER_CHANGED_APPS = 2` (Momo, WPS). Ranking: (1) native/JNI/ABI startup, (2) complex provider/application bootstrap, (3) PendingIntent/Receiver API contracts, (4) Host physical permission capability, (5) virtual package visibility. M12 remains frozen because no audited app reaches Notification/Alarm/Job as its first blocker.

## Recovery Round 2

| App | API | After Recovery Round 2 |
| --- | --- | --- |
| Momo SDK sample (`com.reel.mylibrary`) | 31 | I0/I1 PASS: stable interactive first frame; native path and DB regression remain healthy |
| Momo SDK sample (`com.reel.mylibrary`) | 36 | I0/I1 PASS: stable interactive first frame; arm64 translation path remains usable |
| Chrome (`com.android.chrome`) | 31 | I0/I1 load base + 3 splits and Guest native path; first blocker remains `J.N.ZO` JNI registration |
| Chrome (`com.android.chrome`) | 36 | I0/I1 load base + 3 splits and Guest native path; first blocker remains `J.N.ZO` JNI registration |
| Termux (`com.termux`) | 31 | I0/I1 native setup reaches Activity; first blocker remains PendingIntent mutability contract |
| Termux (`com.termux`) | 36 | I0/I1 native setup reaches Activity; first blocker remains PendingIntent/receiver API contract |
| Coolapk (`com.coolapk.market`) | 31 | I0/I1 native/classloader setup completes; first blocker remains provider/application bootstrap stall |
| Coolapk (`com.coolapk.market`) | 36 | I0/I1 native/classloader setup completes; first blocker remains provider/application bootstrap stall |
| Zhihu (`com.zhihu.android`) | 31 | I0/I1 native initialization reaches ART abort before stable frame |
| Zhihu (`com.zhihu.android`) | 36 | I0/I1 `libDexHelper.so` is AArch64 on x86_64: `GUEST_APP_ABI_INCOMPATIBLE` |
| WPS (`cn.wps.moffice_eng`) | 31 | I0/I1 arm64 native path loads; first blocker remains RePlugin `BinderCursor` null |
| WPS (`cn.wps.moffice_eng`) | 36 | I0/I1 arm64-only `libcp-lib.so` fails ELF load on x86_64: `GUEST_APP_ABI_INCOMPATIBLE` |
| QQ Browser (`com.tencent.mtt`) | 31 | I0/I1 native setup reaches Guest; first blocker remains Host physical network capability |
| QQ Browser (`com.tencent.mtt`) | 36 | I0/I1 package is not registered in current virtual inventory |

Evidence is under `build/reports/real-app-recovery-2/`. `ROUND_1_FIRST_FRAME = 1/7`; `ROUND_2_FIRST_FRAME = 1/7`; split/native audit proves no generic split omission or Guest native search-path bug. Ranking remains (1) native/JNI/ABI startup, (2) complex provider/application bootstrap, (3) PendingIntent/Receiver API contracts, (4) Host physical permission capability, (5) virtual package visibility. M12 remains frozen.

## Recovery Round 3

Round 3 rechecked the unchanged seven-app sample on API31 (`7b670025`) and API36 (`emulator-5554`) with Instance0/Instance1, stopping at the first blocker. Physical Chrome launches were captured for comparison. Guest Chrome still resolves base + `split_chrome` + `split_config.zh` + `split_on_demand`, and its materialized arm64 native directory is present; however no Guest `cr_LibraryLoader`/`System.loadLibrary` success or JNI registration is observed before `J.N.ZO`. The same-version physical Chrome reaches Chrome UI and logs successful `libchrome.so` loading. This does not prove a generic AppSandbox ClassLoader/JNI defect, so no Chrome-specific or fake-JNI change was made.

| App | API31 Round 3 | API36 Round 3 | first blocker |
| --- | --- | --- | --- |
| Momo SDK sample | I0/I1 PASS | I0/I1 PASS | none observed |
| Chrome | I0/I1 FAIL before frame | I0/I1 FAIL before frame | `J.N.ZO` no native implementation; generic runtime defect unproven |
| Termux | first blocker unchanged | first blocker unchanged | PendingIntent/receiver API contract |
| Coolapk | first blocker unchanged | first blocker unchanged | provider/application bootstrap stall |
| Zhihu | first blocker unchanged | `GUEST_APP_ABI_INCOMPATIBLE` | native startup / AArch64 on x86_64 |
| WPS | RePlugin `BinderCursor` null | `GUEST_APP_ABI_INCOMPATIBLE` | RePlugin internal contract / ABI |
| QQ Browser | Host physical network capability | virtual package visibility/registration | platform boundary |

`ROUND_3_FIRST_FRAME = 1/7`; no new first-frame app was obtained. Momo remained the regression oracle on both APIs and both instances. WPS API31 null was localized to the RePlugin internal BinderCursor contract after provider routing, not to the shared JNI/ClassLoader path.

## Recovery Round 4

Round 4 preserved the same seven-app sample and Instance0/Instance1 scope. WPS API31 boundary evidence shows correct authority and ProviderInfo lookup, local Guest provider installation, successful self-route, valid `IContentProvider` transport, and physical-for-system caller identity rewriting. The first null is inside RePlugin `BinderCursor.a(Cursor)`, when its private `BinderCursor$BinderParcelable` is absent; no framework/provider/CursorWindow transport exception is observed. Physical WPS launches normally. No production runtime change was justified.

| App | API31 Round 4 | API36 Round 4 | first blocker |
| --- | --- | --- | --- |
| Momo SDK sample | I0/I1 PASS | I0/I1 PASS | none observed |
| Chrome | unchanged | unchanged | `J.N.ZO` no native implementation |
| Termux | unchanged | unchanged | PendingIntent/receiver API contract |
| Coolapk | unchanged | unchanged | provider/application bootstrap stall |
| Zhihu | unchanged | `GUEST_APP_ABI_INCOMPATIBLE` | native startup / AArch64 on x86_64 |
| WPS | FAIL before frame | `GUEST_APP_ABI_INCOMPATIBLE` | `REPLUGIN_INTERNAL_ASSUMPTION` / ABI |
| QQ Browser | unchanged | unchanged | Host network capability / package visibility |

`ROUND_4_FIRST_FRAME = 1/7`; blocker ranking remains native/JNI/ABI, provider/application bootstrap, PendingIntent/receiver contracts, physical capability, and package visibility. M12 remains frozen.

## Recovery Round 5

Round 5 rechecked the same seven apps with both instances. Termux API31 reaches Guest service startup, then fails in its own `TermuxService.buildNotification()` at `PendingIntent.getActivity()` because targetSdk S+ requires an explicit mutability flag. Termux API36 fails at the Android `registerReceiverWithFeature` contract because neither `RECEIVER_EXPORTED` nor `RECEIVER_NOT_EXPORTED` is supplied after the Host identity boundary. No PendingIntent registry collapse, wrong logical identity, wrong requestCode/type, or delayed receiver routing was reached or proven.

| App | API31 Round 5 | API36 Round 5 | first blocker |
| --- | --- | --- | --- |
| Momo SDK sample | I0/I1 PASS | I0/I1 PASS | none observed |
| Chrome | unchanged | unchanged | `J.N.ZO` no native implementation |
| Termux | FAIL during service startup | FAIL during receiver registration | API version contract / app-specific missing flags |
| Coolapk | unchanged | unchanged | provider/application bootstrap stall |
| Zhihu | unchanged | `GUEST_APP_ABI_INCOMPATIBLE` | native startup / AArch64 on x86_64 |
| WPS | `REPLUGIN_INTERNAL_ASSUMPTION` | `GUEST_APP_ABI_INCOMPATIBLE` | unchanged |
| QQ Browser | unchanged | unchanged | Host network capability / package visibility |

`ROUND_5_FIRST_FRAME = 1/7`; no generic PendingIntent/Receiver production defect was proven, so `VirtualPendingIntentRegistry` was not expanded and M12 remains frozen.

## Recovery Round 6

Round 6 did not change production code. Termux Guest metadata is `targetSdk=28` on both devices, while Host AppSandbox is `targetSdk=36`. On API31, Guest execution uses Host UID 10293 and the system reports change `160794467` enabled for that UID before the Guest PendingIntent failure; physical Termux uses UID 10257, reports the change disabled, and launches normally. This proves client-side targetSdk compat leakage. API36 retains the analogous Host-identity receiver enforcement candidate (`161145287`), but per-Guest VMRuntime/server compat state is not isolated or directly instrumented yet.

| App | API31 Round 6 | API36 Round 6 | first blocker |
| --- | --- | --- | --- |
| Momo SDK sample | I0/I1 PASS | I0/I1 PASS | none observed |
| Chrome | unchanged | unchanged | `J.N.ZO` no native implementation |
| Termux | compat leakage confirmed; PendingIntent mutability failure | compat leakage candidate; dynamic receiver exported flag failure | Host target/UID compat applied to Guest |
| Coolapk | unchanged | unchanged | provider/application bootstrap stall |
| Zhihu | unchanged | `GUEST_APP_ABI_INCOMPATIBLE` | native startup / AArch64 on x86_64 |
| WPS | `REPLUGIN_INTERNAL_ASSUMPTION` | `GUEST_APP_ABI_INCOMPATIBLE` | unchanged |
| QQ Browser | unchanged | unchanged | Host network capability / package visibility |

`ROUND_6_FIRST_FRAME = NOT_RERUN` because no production runtime change was made. The remaining defect is a general per-process targetSdk/compat isolation gap, not a Termux-specific issue; M12 remains frozen.

## Recovery Round 7

Round 7 confirmed the correction boundary but made no production change. The API31 PendingIntent failure occurs in framework `PendingIntent.checkFlags` before Binder, while the existing `VirtualPendingIntentRegistry` is not connected to static PendingIntent factories. A safe fix would require a new Guest-aware framework-call interception boundary; global `VMRuntime` or compat mutation remains forbidden. Dynamic receiver translation was not applied without a complete, verified API-signature/legacy-semantic mapping.

| App | API31 Round 7 | API36 Round 7 | first blocker |
| --- | --- | --- | --- |
| Momo SDK sample | I0/I1 PASS (revalidated) | I0/I1 PASS (revalidated) | none observed |
| Chrome | observe only | observe only | `J.N.ZO` no native implementation |
| Termux | compat leakage remains; PendingIntent checkFlags | compat leakage remains; receiver flags | Guest-aware framework interception missing |
| Coolapk | unchanged | unchanged | provider/application bootstrap stall |
| Zhihu | unchanged | `GUEST_APP_ABI_INCOMPATIBLE` | native startup / AArch64 on x86_64 |
| WPS | observe only | `GUEST_APP_ABI_INCOMPATIBLE` | RePlugin assumption / ABI |
| QQ Browser | unchanged | unchanged | Host network capability / package visibility |

`ROUND_7_FIRST_FRAME = 1/7`; no additional app advanced. M12 remains frozen.

## Recovery Round 8

Round 8 architecture review selected no client interception architecture. M10 proves one Guest `VirtualProcessKey` per READY physical slot/generation, but a process-global compat delegate cannot be safely isolated from Host runtime state and the hidden API is not exposed in the compile SDK. Candidate B is also unproven because PendingIntent factories/checks execute before Binder and the existing registry is not wired to a Guest-only framework call boundary. No production runtime changes were made.

`ROUND_8_FIRST_FRAME = NOT_RERUN`; Momo remains the API31/API36 I0/I1 PASS oracle. M12 remains frozen.

## Recovery Round 9

Round 9 found no safe Guest-only PendingIntent interception: the project has Bionic IO hooks but no ART method hook with a reliable original-call path, and Guest DEX rewriting would not safely cover split/dynamic DEX or integrity checks. PendingIntent remains blocked before Binder. A separate Guest-caller-gated `IActivityManager` receiver translation now preserves pre-33 semantics without changing Host calls, sticky queries, protected-only filters, explicit flags, or modern Guest policy.

| App | API31 Round 9 | API36 Round 9 | first blocker |
| --- | --- | --- | --- |
| Momo SDK sample | I0/I1 PASS | I0/I1 PASS | none observed |
| Chrome | I0/I1 unchanged | I0/I1 unchanged | `J.N.ZO` no native implementation |
| Termux | I0/I1 PendingIntent failure | I0/I1 receiver fixed, then PendingIntent failure | Guest-only PendingIntent interception missing |
| Coolapk | I0/I1 bootstrap stall | I0/I1 bootstrap stall | provider/application bootstrap |
| Zhihu | I0/I1 ART `SIGABRT` | I0/I1 `GUEST_APP_ABI_INCOMPATIBLE` | native startup / AArch64 on x86_64 |
| WPS | I0/I1 RePlugin assumption | I0/I1 `GUEST_APP_ABI_INCOMPATIBLE` | RePlugin private contract / ABI |
| QQ Browser | I0/I1 Host network capability | I0/I1 package registration failure | physical capability / visibility |

API36 Termux I0/I1 logs prove `guestTarget=28`, `guestCall=true`, and receiver flags `0 -> 2`; the prior exported-flag exception is absent and the next first blocker is PendingIntent mutability. `ROUND_9_FIRST_FRAME = 1/7`; M12 remains frozen and M13 was not started. Evidence is under `build/reports/real-app-recovery-9/`.
