STATUS = PARTIAL (Play Console not rerun; user requested no reinstall)
EXPANSION_2_CLASSIFICATION_CORRECTED = YES; CLASSLOADER/INTERNET/SPLIT_NATIVE = FIXED
API31_FIRST_FRAME = 5/31 -> 25/31 dual-instance PASS; Douyin Lite additionally reaches feed but is not counted
API31_PASS = Arrow,Baidu,Coolapk,Damai,DamaiHelper,Detector,DingTalk,DragonRead,Duokan,HideAppList,iQIYI,Incy,Kuaixun,KugouLite,Lark,Momo,MusicPlayGo,NetworkToolbox,PDFEditor,QQBrowser,Qunar,Sudoku,SuperList,WeChat,Zhihu
REMAINING = Chrome; DouyinLite measurement; EnhanceFox APP_PROTECTION; Termux frozen PendingIntent; WPS RePlugin; PlayConsole not installed
GUEST_INTERNET_MANIFEST_ENFORCEMENT = NOT_IMPLEMENTED; INTERNET/NETWORK_STATE_OVERGRANT = YES
SPLIT_NATIVE_FIXTURE = PASS API31 arm64 + API36 x86_64; isolated split loading not modelled
STUB_SERVICE_POOL = PASS; 31 Guest stubs/slot, index 0 Host-only, destroyed-stub oldest-first reclaim
SERVICE_STRESS = PASS API31/API36 I0/I1; wave1 28/28 + wave2 20/20, 40 distinct main-process Services
SERVICE_REGRESSION = PASS WeChat,QQBrowser,Baidu,iQIYI,KugouLite,DingTalk,Momo I0/I1; no FATAL/interface mismatch/pool exhaustion
API36_LATEST = Momo,NetworkToolbox,R15 fixture PASS I0/I1; emulator-5554 stopped after validation
TERMUX_RECEIVER = PASS flags 0->2; M12 remains frozen
FIXTURE_INSTALL_NOTE = current device fixture installs are base APK, not prior split install; native load still PASS
KNOWN_LIMIT = Provider acquire still round-trips coordinator; WPS/Chrome frozen; Douyin first-frame measurement pending
BUILD_GATES = PASS (testDebugUnitTest, assembleDebug, assembleRelease; arm64-v8a/x86_64)
EVIDENCE = build/reports/m15/api31t, build/reports/m15/api36t, build/reports/m15/sweep-s17.txt
RUNTIME_COMMIT = 9b47f4ddc5dcfe321fc586e24d35fe9ed4893e41; PUSHED = NO
M12_REMAINS_FROZEN = YES; M13_STARTED = NO; STOPPED_AFTER_GENERIC_RUNTIME_CORRECTION_15 = YES
