# M11 CLOSURE VALIDATION RESULT
START_BRANCH = main; START_HEAD = a2bd63943cf63d22e8d713e9d4b6c8228457cadc
API36_ENV = PASS: emulator-5554, SDK 36, x86_64, google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.E1/13823249
API31_DEVICE = PASS: 7b670025, SDK 31, arm64-v8a, Xiaomi/umi/umi:12/SKQ1.220303.001/22.10.26
API31_M11 = PASS: 8/8; M11_ISOLATION = PASS: 3/3; focused current-head evidence retained
API36_M11 = PASS: 8/8; M11_ISOLATION = PASS: 3/3; fixed/route/provider/last/current/listener/threading/removeUpdates all PASS
REQUEST_INTERVAL = PASS; MIN_DISTANCE = PASS; MAX_UPDATES = PASS; DURATION = PASS; filtered route points advanced cursor and later eligible point delivered
VIRTUAL_LOCATION_COORDINATOR_PROVEN = YES; LOCATION_SERVICE_INTERCEPTION_PROVEN = YES; FIXED_LOCATION_PROVEN = YES; ROUTE_LOCATION_PROVEN = YES
MULTI_INSTANCE_LOCATION_ISOLATION_PROVEN = YES: I0 35.11/139.11, I1 34.22/135.22, default+remote on both devices
MULTI_PROCESS_LOCATION_CONSISTENCY_PROVEN = YES; PROCESS_DEATH_LOCATION_CLEANUP_PROVEN = YES; STALE_LOCATION_CALLBACK_REJECTION_PROVEN = YES
INSTANCE_DELETE = PASS: I0 profile ABSENT and I1 FIXED after deletion on API31/API36; registrations/runtime cleanup logged
INSTANCE_RECREATE = PASS: normal I0 recreation remains ABSENT and does not inherit deleted profile; I1 remains FIXED
LOCATION_PROFILE_PERSISTENCE_PROVEN = YES; process-local registrations intentionally not persisted; LOCATION_PENDING_INTENT = DEFERRED
GNSS_STATUS_POLICY = SUPPRESSED_IN_VIRTUAL_MODE; GNSS_STATUS_LIFECYCLE_PROVEN = YES; raw GNSS/NMEA/GMS/Geocoder = OUT_OF_SCOPE
COARSE_PRECISE_PERMISSION_BEHAVIOR = DEFERRED_TO_M12; LOCATION_IS_MOCK_API31 = false; LOCATION_IS_MOCK_API36 = false
M2-M10 API31 = Demo1 PASS; M7 PASS; M10 PASS; M8 runtime PASS with known manifest AUTOMATION_HARNESS timeout; M9 25 PASS + 1 BLOCKED + 2 MANUAL scope/harness
M2-M10 API36 = Demo1 PASS; M7 PASS; M10 PASS; M8 runtime PASS with same AUTOMATION_HARNESS timeout; M9 25 PASS + 1 BLOCKED + 2 MANUAL scope/harness
BUILD_GATES = PASS: :app:testDebugUnitTest, :app:assembleDebug, :app:assembleRelease; arm64-v8a/x86_64 compile outputs present
STATUS = PARTIAL (M11 core and dual-device closure PASS; retained M2-M10 harness/scope debt prevents final closure classification)
M11_READY_TO_CLOSE = NO; M12_STARTED = NO; STOPPED_AFTER_M11_CLOSURE = YES
FINAL_HEAD = focused commit recorded by this closure; COMMITS = docs(m11): close API36 validation; EVIDENCE = build/reports/m11/*-m11-final*.log, *-isolation*.log, *-instance-recreate-final.log, build-*.log
