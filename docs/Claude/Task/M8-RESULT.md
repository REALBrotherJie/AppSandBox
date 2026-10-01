# M8 RESULT
STATUS: PARTIAL
START HEAD: 520acbd
FINAL HEAD: pending focused commit
COMMITS: pending

RECEIVER_ARCHITECTURE: Added instance-scoped VirtualReceiverManager, physical StubReceiver pool (p0..p8), manifest declarations and lifecycle bookkeeping tests. StubReceiver only probes the framework; it does not invoke Guest onReceive.
PROVIDER_ARCHITECTURE: Added instance+authority keyed VirtualProviderManager and install/cleanup tests. Existing ContentProviderIdentityAdapter remains physical-system identity wrapping only.
API31: app build/install/start PASS; M2 Activity pipeline observed. M8 receiver/provider hard suite NOT RUN/PASS (no ActivityThread receiver/provider transaction restoration yet).
API36: app build/install/start PASS; M2 Activity pipeline observed. M8 receiver/provider hard suite NOT RUN/PASS (no ActivityThread receiver/provider transaction restoration yet).
DEMO2 M8: BLOCKED at real manifest/dynamic/ordered receiver dispatch and real provider install/CRUD; no fake lifecycle used.
REGRESSION: unit tests, assembleDebug, assembleRelease PASS. Demo1/M6/M7/Demo3 full device regression not rerun in this partial implementation.
RISK/SECURITY: manager keys bind instanceId; deleted-instance cleanup is covered at model level. Stub envelope validation and framework transaction restoration remain unimplemented.
LIMITATIONS: no ReceiverPlatformBridge, no scheduleReceiver restore, no LoadedApk ReceiverDispatcher routing, no ActivityThread installProvider/attachInfo ordering, no authority acquisition routing, no remote process support.

GUEST_MANIFEST_RECEIVER_PROVEN = NO
GUEST_DYNAMIC_RECEIVER_PROVEN = NO
ORDERED_BROADCAST_SEMANTICS_PROVEN = NO
GUEST_CONTENT_PROVIDER_PROVEN = NO
PROVIDER_INSTANCE_ISOLATION_PROVEN = PARTIALLY (model only)
M2_M3_M4_M5_M6_M7_REGRESSION = PARTIALLY VERIFIED
API31 = PARTIAL
API36 = PARTIAL
M8_READY_TO_CLOSE = NO
