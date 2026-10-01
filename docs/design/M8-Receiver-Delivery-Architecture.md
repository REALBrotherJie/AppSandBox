# M8 Receiver Delivery Architecture

## Decision

Select **A. FRAMEWORK_OWNED_MULTI_STUB_ORDERED_DELIVERY**. API31 and API36 each proved that one real AMS ordered broadcast resolves two physical receivers, propagates A's code/data/extras to B, suppresses B after `abortBroadcast()`, and holds B behind `goAsync()` until `PendingResult.finish()`. `VIRTUAL_ORDERED_COORDINATOR` is rejected because it would duplicate Android's authoritative ordered state and timeout machinery.

## Ownership

The `:vs` process owns a logical `BroadcastSession`: cryptographically random session id, package, instance id, virtual uid, process slot, original Intent, ordered flag, sorted receiver count, rank-to-Guest `ActivityInfo`, terminal receiver metadata, creation time, and lifecycle. It stores diagnostics snapshots only; resultCode/data/extras, abort, PendingResult, finish barrier, timeout, and final completion remain authoritative in AMS/ActivityThread.

Each physical delivery is owned by Android: AMS resolves a predeclared ranked StubReceiver, creates one ReceiverData, ActivityThread restores that rank to the mapped Guest receiver, invokes the real Guest class through `handleReceiver`, and reports the real `finishReceiver`. No Guest receiver is constructed or invoked by AppSandbox.

## Stub Mapping And Ordering

Use receiver-count-specific internal action groups, for example `ORDERED_P1_N2`, with exactly N statically declared rank receivers in the target slot and fixed descending priorities. This excludes unused stubs without temporary component enablement, no-op deliveries, or global mutable targets. Guest receivers are sorted by manifest priority and stable VPM resolution order, then mapped rank-for-rank. The logical Guest action is restored before dispatch.

The proxy Intent contains only an opaque session id and rank-independent authentication material. On every H.RECEIVER delivery the stub process queries `:vs`, then validates package, instance, slot, physical stub component/rank, expiry, and single-consume state. Forged, replayed, expired, or mismatched deliveries finish without Guest dispatch. Session cleanup occurs after terminal completion, abort, timeout, instance deletion, or owner/process death.

## Cold Start And Concurrency

Cold lookup cannot depend on a stub-process static map. `:vs` is the authoritative session owner and must be startable/bindable before ReceiverData restoration; it returns runtime identity plus receiver metadata so the slot process performs Guest Application/provider bootstrap and then resumes framework dispatch. API36 proved cold physical process start. API31 MIUI blocked shell-to-stopped-package implicit delivery, while the same two-receiver chain passed warm; product cold proof still requires an in-app/:vs sender rather than shell.

Concurrent sessions are isolated exclusively by session id. A stub component may serve many sessions, but no `currentDelivery`, current receiver, or static target is permitted. AMS serializes each ordered chain independently and retains its own result/PendingResult state.

## Dynamic And Manifest Interaction

Ordinary dynamic registration/delivery remains proven. Its prior `PARTIALLY` label means `INSTANCE_ROUTING_NOT_PROVEN` and `ORDERED_DYNAMIC_RECEIVER_NOT_PROVEN`, not a regression. M8 closure should first use the ranked physical chain for resolved manifest receivers. A later mixed manifest/dynamic extension must feed both kinds into one VPM ordering result and ranked physical chain; it must not run a second AppSandbox ordered state machine. Real system broadcasts keep their normal physical dynamic registration path.

## Platform Boundary

API-specific ReceiverData/PendingResult fields and `broadcastIntent*` signatures belong only in PlatformBridge. API31 and API36 both exposed ordered PendingResult behavior; diagnostics observed distinct A/B PendingResult objects, ordered=true, framework result transfer, abort suppression, and an approximately 800 ms goAsync barrier. Business routing must not depend on private field names.

## Provider Finding

Provider isolation is **FAIL**, not unverified. API36 clean instances in p1/p2 showed B initially empty, B insert, then A observed B's row. Both instance `databases/` directories stayed empty while data landed in Host `databases/demo2.db`. Root cause classification: `GUEST_PROVIDER_CONTEXT_APPLICATION_CONTEXT_ESCAPE`; `DemoContentProvider` asks `context.applicationContext`, escaping the Guest wrapper to Host Context. API31 showed the same Host database/empty instance-root structure; this review does not modify Provider Runtime.

## Closure Work

The next Planner-authorized M8 closure implementation must add ranked per-slot stub groups, `:vs` session IPC/validation/cleanup, H.RECEIVER rank restoration, cold bootstrap proof on both devices, concurrent/replay tests, Guest A/B ordered tests, and mixed-ordering scope enforcement. Provider context/applicationContext isolation requires a separate authorized correction. M8 remains open and M9 has not started.
