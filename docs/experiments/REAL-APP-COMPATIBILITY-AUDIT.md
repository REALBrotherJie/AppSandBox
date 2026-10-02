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
