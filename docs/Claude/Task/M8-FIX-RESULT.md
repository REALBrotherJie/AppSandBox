# M8-FIX RESULT
STATUS: PARTIAL; START `df0e59f`; FINAL: this RESULT commit; COMMITS: `0ee6b98` + result commit; historical untracked task files retained.
FOUNDATION_GATE_BEFORE_FIX: PASS; API31/API36 M2-M6 Demo1 CORE `26 PASS/0 FAIL/0 BLOCKED`; M7 local Service scope `8 PASS`, remote-process remains M10 deferred.
FOUNDATION_REGRESSIONS_FOUND: none; Demo3 metadata/VPM sourceDir/splits/nativeLibraryDir/API ABI PASS on API31/API36; Momo native `.so` remains M9 deferred.
ROOT_CAUSES: `registerReceiverWithFeature` leaked Guest caller package; package-scoped sends missed physical dynamic registration/manifest routing; ReceiverData fields lacked reflective access; local Provider notifications/observers escaped to physical ContentService.
FIXES: physical IAM identity rewrite; dynamic physical package namespace; PM-resolved deliveryId StubReceiver route; accessible H.RECEIVER restore; real installProvider local-map verification; instance authority registry; local ContentService notification/observer route.
DYNAMIC_RECEIVER: `registerReceiverWithFeature` arg1 `com.reel.demo2→com.example.appsandbox`; real ReceiverDispatcher callback PASS API31/API36; two-instance isolation not proven.
MANIFEST_RECEIVER: deliveryId→H.RECEIVER(113)→ReceiverData Intent/ActivityInfo restore→ActivityThread.handleReceiver→real Guest onReceive→finishReceiver; warm PASS API31/API36; cold/goAsync not proven.
ORDERED: PARTIAL; real OrderedReceiverA ran and propagated `code=42,data=set-by-A`; second framework delivery to OrderedReceiverB was not scheduled, so final `43/final-by-B` FAIL API31/API36.
CONTENT_RESOLVER: prior Host/system fallthrough removed for installed Guest authority; system authorities retain physical M6 path; no StubProvider CRUD and no exported change.
PROVIDER: real `ActivityThread.installProvider`, Guest class instantiate/attachInfo/onCreate, `mLocalProvidersByName` + `mProviderMap`, `acquireExistingProvider=true` proven API31/API36.
PROVIDER_API31/API36: INSERT/QUERY/UPDATE/DELETE and ContentProviderClient PASS; two-instance/restart/delete-recreate lifecycle not proven in this FIX.
FOUNDATION_GATE_AFTER_FIX: PASS; API31/API36 Demo1 CORE `26/26`; M7 local scope `8/8`; remote-process outcome unchanged and deferred to M10.
BUILD: `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease` PASS; raw evidence remains ignored under `build/reports/m8-fix/`/device logcat.
GOVERNANCE: `docs/Claude/README.md` and `01-方向A总体规划.md` updated to M2-M14, review gate, compatibility risks, and `M<N> + M<N>-FIX` maximum.
FIRST_RECEIVER_BLOCKER: ordered broadcast requires a multi-receiver physical delivery chain preserving Binder finish token/result state; current single StubReceiver route resolves two but dispatches only the first.
FIRST_PROVIDER_BLOCKER: none for single-instance CRUD/client; lifecycle/isolation matrix remains unexecuted because ordered hard gate failed first.
ARCHITECTURAL_ASSUMPTION_FAILED: one physical explicit StubReceiver delivery cannot represent an ordered list; M8 receiver routing needs redesign/review, not another numbered closure task.
FOUNDATION_GATE=PASS; GUEST_MANIFEST_RECEIVER_PROVEN=PARTIALLY; GUEST_DYNAMIC_RECEIVER_PROVEN=PARTIALLY; ORDERED_BROADCAST_SEMANTICS_PROVEN=NO; GUEST_CONTENT_PROVIDER_PROVEN=YES; PROVIDER_INSTANCE_ISOLATION_PROVEN=NO.
M2_M3_M4_M5_M6_M7_REGRESSION=PASS; API31=FAIL; API36=FAIL; M8_READY_TO_CLOSE=NO; M8_FIX_FAILED_TO_CLOSE=YES; STOPPED_BEFORE_M9=YES.
