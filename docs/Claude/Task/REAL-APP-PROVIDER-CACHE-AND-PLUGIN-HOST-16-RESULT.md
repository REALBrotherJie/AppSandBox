# Real App Recovery Round 16 Result
STATUS = PASS
DEVICE = 7b670025 (API31)
API36 = NOT_MEASURED (user scope: real device only)
PLATFORM_REMOTE_PROVIDER_CACHE_KEY = ProviderKey(authority,userId); ref counts use IContentProvider.asBinder()
APPSANDBOX_CACHE_MISS_REASON = remote holder used Guest virtual UID (wrong user key) and wrapper returned a new Binder facade
PROVIDER_CACHE_FIXED = YES
PROVIDER_ROUTES_BEFORE = 215; PROVIDER_ROUTES_AFTER = 4 per instance (1 per authority)
PROVIDER_DEATH_REROUTE = PASS
WPS_PIT_PROVIDER_IN_DEX = NO
WPS_PHYSICAL_PIT_PROVIDER_LOADER = com.qihoo360.replugin.RePluginClassLoader
WPS_APPSANDBOX_PIT_PROVIDER_LOADER = com.qihoo360.replugin.RePluginClassLoader
WPS_CLASSIFICATION = PROVIDER_CLASSLOADER_NOT_LOADEDAPK (preflight violated platform substitution contract)
WPS_RUNTIME_FIXED = YES
WPS_FIRST_FRAME = FAIL
WPS_NEXT_BLOCKER = PLUGIN_RUNTIME_STATE: Tinker child-process bind returns connection is null
FIXTURE_REGRESSION = PASS (I0/I1: services 28/28 + 20/20, external/process/native PASS)
ROUND_15_FIRST_FRAME = 25/31; ROUND_16_FIRST_FRAME = 25/31
BUILD_GATES = PASS
M12_REMAINS_FROZEN = YES; M13_STARTED = NO; STOPPED_AFTER_RECOVERY_ROUND_16 = YES
