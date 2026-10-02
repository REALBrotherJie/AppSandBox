# REAL APP COMPATIBILITY AUDIT RESULT
START_BRANCH = main; START_HEAD = 57c31b41aec3ad00feb58c341e1152e5c6d86185; PRODUCTION_RUNTIME_MODIFIED = NO
DEVICES = API31 7b670025/arm64-v8a; API36 emulator-5554/x86_64 restored with AppSandbox_API36_Secondary
APPS = Momo SDK sample, Chrome, Termux, Coolapk, Zhihu, WPS, QQ Browser; two logical instances attempted per API
MOMO = Application and SplashActivity CREATE/RESUME on both APIs; first blocker is WorkManager DB double-prefix -> SQLITE_CANTOPEN
MOMO_M9 = native IO initialized; current first failure is M5/M9-supported path rewriting, not direct-syscall out-of-scope
CHROME = startup blocked before first frame: missing Chromium JNI implementation on API31; null Context during Chromium async init on API36
TERMUX = Activity reached; API31 first blocker PendingIntent mutability contract, API36 dynamic Receiver export contract
WPS = non-exported RePlugin provider physical-UID denial; API36 also has arm64/x86_64 native incompatibility
QQ_BROWSER = API31 Host physical ACCESS_NETWORK_STATE denial; API36 virtual package registration/visibility blocker
COOLAPK/ZHIHU = complex provider/native startup does not reach stable first frame; Zhihu API36 is arm64-only ABI incompatible
REAL_MULTIPROCESS = NOT PROVEN; REAL_WEBVIEW = NOT PROVEN; Notification/Alarm/Job demand not reached naturally
TOP_FIRST_BLOCKER = M2-M10_STARTUP_AND_IDENTITY_BOUNDARIES; RECOMMEND_RESUME_M12 = NO
STATUS = PASS; APPS_TESTED = 7; API31_APPS_TESTED = 7; API36_APPS_TESTED = 7
APPS_REACHING_FIRST_FRAME = 0; APPS_REACHING_CORE_INTERACTION = 0
APPS_PROVING_REAL_MULTIPROCESS = 0; APPS_PROVING_REAL_NATIVE = 1; APPS_PROVING_REAL_WEBVIEW = 0
MOMO_RESULT = FAIL; M12_REAL_APP_DEMAND_OBSERVED = NO
M13_STARTED = NO; PRODUCTION_RUNTIME_MODIFIED = NO; STOPPED_AFTER_REAL_APP_AUDIT = YES
FINAL_HEAD = focused audit documentation commit; COMMITS = docs: record real app compatibility audit
