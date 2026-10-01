# M8 ARCH REVIEW RESULT
STATUS: PASS (architecture uncertainty closed; M8 remains open); START branch `main`; START HEAD `dccbe48`; FINAL: this RESULT commit.
COMMITS: `c90df4e` framework probes + decision/RESULT commit; files: debug manifest/probe receivers, architecture decision, RESULT.
PROBE: debug-only priority 100/50 physical receivers; one real `am broadcast` ordered session; no Guest production rewrite and no manual receiver invocation.
API31 PROVEN: A→B; B observed `42/set-by-A/source=A`; abort omitted B; goAsync A returned at 167091628, finished 167092430, B entered 167092439.
API36 PROVEN: cold AMS start plus A→B; B observed `42/set-by-A/source=A`; abort omitted B; goAsync returned 2359741, finished 2360542, B entered 2360544.
PENDINGRESULT OBSERVED: distinct real A/B PendingResult objects, ordered=true, abort=false/finished=false on entry; framework carried result and finish barrier.
COLD START: API36 physical process start proven; API31 MIUI shell broadcast to stopped package blocked, while warm framework chain passed; production session lookup must live in `:vs`, never stub static memory.
SELECTED: `FRAMEWORK_OWNED_MULTI_STUB_ORDERED_DELIVERY`; rejected `VIRTUAL_ORDERED_COORDINATOR` because it duplicates authoritative result/abort/goAsync/timeout state.
MODEL: `:vs` owns opaque session/package/instance/slot/rank mapping and cleanup; AMS owns sequencing/results/abort/goAsync/finish/timeout; count-specific priority stub action groups exclude unused ranks.
CONCURRENCY/SECURITY: session-scoped random id; validate package/instance/slot/stub rank/expiry/single-consume; no global current receiver; cleanup on terminal/abort/timeout/delete/death.
DYNAMIC: normal registration/delivery remains PASS; prior PARTIAL=`INSTANCE_ROUTING_NOT_PROVEN + ORDERED_DYNAMIC_RECEIVER_NOT_PROVEN`; mixed ordering must later use the same ranked chain.
PROVIDER API36 FAIL: clean B query=0, B insert, then A query=1; p1/p2 distinct but both instance database dirs empty and Host `databases/demo2.db` shared.
PROVIDER API31 OBSERVED FAIL STRUCTURE: distinct slots/local maps but the same Host DB path with empty instance database roots; UI row probe blocked by MIUI uiautomator failure.
PROVIDER ROOT CAUSE: `GUEST_PROVIDER_CONTEXT_APPLICATION_CONTEXT_ESCAPE`; Provider uses `context.applicationContext`, reaching Host Context; no Provider rewrite performed.
BUILD: `testDebugUnitTest`, `assembleDebug`, `assembleRelease` PASS; raw evidence ignored under `build/reports/m8-arch-review/`.
ARCH_REVIEW_STATUS=PASS; SELECTED_RECEIVER_ARCHITECTURE=FRAMEWORK_OWNED_MULTI_STUB_ORDERED_DELIVERY; MULTI_PHYSICAL_RECEIVER_DELIVERY_PROVEN=YES.
FRAMEWORK_ORDERED_RESULT_PROPAGATION_PROVEN=YES; FRAMEWORK_ABORT_PROPAGATION_PROVEN=YES; FRAMEWORK_GOASYNC_BARRIER_PROVEN=YES.
COLD_START_SESSION_MODEL_PROVEN=YES; CONCURRENT_SESSION_MODEL_DEFINED=YES; PROVIDER_INSTANCE_ISOLATION=FAIL; API31=PASS; API36=PASS.
M8_CLOSED=NO; M9_STARTED=NO; STOPPED_AFTER_ARCH_REVIEW=YES.
