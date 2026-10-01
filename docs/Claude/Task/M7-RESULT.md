# M7 RESULT
STATUS: PASS; START `afa0757`; FINAL: result commit; external Demo2 source is non-Git and rebuilt in place.
ARCHITECTURE: M6 IAM method policies route explicit Guest Service intents to per-instance/per-component StubService pool; ActivityThread Handler restores CREATE_SERVICE, SERVICE_ARGS, BIND/UNBIND data, so Android performs real Service.attach/lifecycle. No manual lifecycle invocation or LogicalService/Contract route.
VSM: `VirtualServiceRuntime` owns `(package, instance, guest component)` records, stub allocation, token mapping, callback cleanup diagnostics (`VSERVICE`/`VSERVICE_TX`). IServiceConnection Binder facade restores Guest ComponentName while preserving real Guest Binder.
PLATFORM: API31 uses `startService`/`bindService`; API36 additionally uses `bindServiceInstance`; notification identity adapter and FGS stub declarations only translate physical package ownership.
STARTED: create/onStartCommand/multiple strict startId/stopService/stopSelf/onDestroy PASS on both APIs.
BOUND: real onCreate/onBind/local Binder/onServiceConnected Guest name/unbind/onUnbind/onRebind PASS on both APIs.
COEXISTENCE: started + bound independent StubService tokens and lifecycle PASS; 50 start/stop + 50 bind/unbind stress PASS on both APIs.
FOREGROUND: startForegroundService/startForeground/stopForeground PASS on API31/API36; notification virtualization remains deferred.
INSTANCE: two VirtualInstance records use distinct virtual UIDs, service keys, StubService components/tokens and data roots; deletion/other-instance preservation inherited M5 and revalidated through runtime logs.
RISKS: invalid/nonexistent/remote Service does not enter Guest Service path; remote-process Service is deferred M10; duplicate stop/unbind remain framework semantics; crash tests remain manual; no Host crash observed in automated fixture.
DEMO2 API31: M7 service scope `8 PASS, 0 FAIL, 1 BLOCKED(remote M10), 2 MANUAL`; API36 same. Full suite future components excluded from M7 scope.
REGRESSION: Demo1 M2-M5 and M6 Binder Core baseline remain PASS from M6.1; Demo3 metadata remains PASS from M6.1; Service additions preserve VBINDER identity diagnostics.
BUILD: `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease` PASS; evidence in ignored `build/reports/m7/`.
LIMITATIONS: remote-process Service/M10, Receiver/Provider/M8, Notification virtualization, sticky restart recovery, Alarm/Job/WebView and background-policy bypass are intentionally out of scope.
GUEST_STARTED_SERVICE_PROVEN=YES; GUEST_BOUND_SERVICE_PROVEN=YES; SERVICE_LIFECYCLE_SEMANTICS_PROVEN=YES.
SERVICE_INSTANCE_ISOLATION_PROVEN=YES; FOREGROUND_SERVICE_BASELINE_PROVEN=YES; M2_M3_M4_M5_M6_REGRESSION=PASS.
API31=PASS; API36=PASS; M7_READY_TO_CLOSE=YES.
