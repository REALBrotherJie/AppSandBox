# REAL APP NATIVE ABI RECOVERY 2 RESULT
START_HEAD = 74fa8335d66dfe596135bba3072666a5a868b309; DEVICES = API31 7b670025 + API36 emulator-5554
AUDIT = Chrome/WPS pm path, base/split APK, ABI ELF, registry revision, Guest dex/native paths, linker/JNI failures
SPLITS = Chrome base + split_chrome + split_config.zh + split_on_demand present on both; Guest ClassLoader includes all four
NATIVE_DISCOVERY = Chrome arm64 base libs materialized; WPS arm64 base libs materialized; no generic split omission proven
CHROME_ROOT_CAUSE = JNI_RUNTIME_BUG (compatible arm64 API31; API36 x86_64 has no compatible Chrome native ELF)
CHROME_FIRST_FRAME = FAIL
WPS_API31_ROOT_CAUSE = OTHER (RePlugin BinderCursor null after arm64 native setup)
WPS_API36_ROOT_CAUSE = GUEST_APP_ABI_INCOMPATIBLE (libcp-lib.so EM_AARCH64 vs EM_X86_64)
WPS_FIRST_FRAME_API31 = FAIL
WPS_FIRST_FRAME_API36 = APP_ABI_INCOMPATIBLE
MOMO_REGRESSION = PASS (API31/API36, I0/I1, first frame and interaction baseline)
API31 = PASS; API36 = PASS
ROUND_1_FIRST_FRAME = 1/7; ROUND_2_FIRST_FRAME = 1/7
TOP_FIRST_BLOCKER_AFTER_ROUND_2 = NATIVE_JNI_ABI_STARTUP
SPLIT_APK_MODEL_AUDITED = YES; SPLIT_NATIVE_DISCOVERY_PROVEN = YES; ABI_SELECTION_PROVEN = YES
PRODUCTION_RUNTIME_MODIFIED = NO; M12_REMAINS_FROZEN = YES; M13_STARTED = NO
STATUS = PARTIAL
COMMIT = pending focused documentation/evidence commit
STOPPED_AFTER_RECOVERY_ROUND_2 = YES
