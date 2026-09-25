# Activity Client Adapter Plan

状态：PREFLIGHT ONLY，2026-09-25。Positive implementation 未授权。

## Version boundary

API31 与 API36 使用独立 adapter descriptor。每个 descriptor 必须先发现并验证 runtime 类型、成员和顺序，不能共享硬编码字段，也不能把 OEM 差异视为兼容。

## Stages

| Stage | Purpose | Allowed result | Authorization |
|---|---|---|---|
| P0 Observation-only | observe launch transaction/client record runtime shape and order | `INTERNAL_SURFACE_OBSERVED` or `FAILED_CLOSED` | recommended next experiment |
| P1 Pre-attach selection | test whether Guest class/classloader selection is reachable before attach, retaining Host fallback | selection reached/unreachable; no attach | NOT AUTHORIZED |
| P2 Attach/substitution | study Guest object reuse of Host carrier token/window/task | requires complete Context/Application/ActivityInfo/config/Window/rollback | DEFERRED, NOT AUTHORIZED |

P0 不修改 transaction、不替换 class、不构造 Guest Activity、不调用 attach。P1 也不得执行 attach 或 Guest lifecycle。P2 不会因 P0/P1 成功而自动授权。

## State machine

```text
NOT_STARTED
 -> HOST_LAUNCH_RECEIVED
 -> INTERNAL_SURFACE_OBSERVED
 -> GUEST_MAPPING_FOUND
 -> PRE_ATTACH_SELECTION_ATTEMPTED
 -> FALLBACK_TO_HOST
or FAILED_CLOSED
```

`GUEST_ATTACHED`、`GUEST_LIFECYCLE_STARTED` 和 `L3_CONFIRMED` 不属于当前授权状态。

## Access mechanism comparison

| Mechanism | Observation/change | API31/API36 and targetSdk 36 | Dependency/production risk | Decision |
|---|---|---|---|---|
| Debug-only reflection | inventory accessible internal objects without mutation | version/OEM sensitive; non-SDK access may be denied | no dependency; debug only | P0 candidate, fail closed |
| Method hook library | broad argument observation but changes dispatch | device/runtime sensitive | new dependency and contamination risk | reject for P0 |
| Custom Instrumentation | sees factory/public callbacks but replacement changes framework state | incomplete transaction visibility | manifest/runtime replacement risk | reject for P0 |
| Handler callback | may see messages but modern transaction payload ownership is internal | legacy recipes do not generalize | mutates main-thread dispatch | reject |
| ClientTransaction/TransactionExecutor observation | best semantic surface when reachable without mutation | separate adapters; hidden access may fail | no production code allowed | preferred surface |
| JVMTI/debug tooling | can trace methods/classes in suitable debug environments | attach/support varies by device | tooling complexity | alternate/deferred |
| Root/Xposed | broad observation and hooks | environment-specific | root/module requirement | reject for first probe |
| AOSP-instrumented build | authoritative internal tracing | not representative of stock OEM access | custom system image | reference/deferred |

## Fail-closed and rollback

Immediate abort conditions: API shape mismatch, denied access, Host lifecycle order change, missing Host token/window, Host crash/ANR, Guest installation, Guest lifecycle call, unavailable Host fallback, or evidence that cannot distinguish Host from Guest.

P0 catches observation failures outside the Host launch path and leaves the Host transaction untouched. P1, if later authorized, must complete all verification before class/client-record mutation and retain original Host values for immediate fallback. Once transaction data is mutated, rollback is conditional; once `Activity.attach` begins, rollback is not considered safe. P2 requires a new atomicity review.

## Security and release boundary

All adapters remain debug-only. Release contains no adapter class, reflection strings, hook dependency, alternate Instrumentation or task entry. Non-SDK enforcement is never bypassed; denial is an experiment result. Binder interception, Hook and Root/Xposed are not required for P0.

## P0 evidence matrix

Run separately on API31 Xiaomi Mi 10 and API36 emulator-5554. Capture device/API/ABI/page size, Host APK SHA, Guest revision/SHA, Host Stub component/task/window/token, observed transaction/client-record type, observation ordering, Host lifecycle/fallback, Guest install state, access exceptions, crash/ANR status, logcat and dumpsys activity/window.

## Decision

The smallest useful next experiment is `ACT-004B-P0 observation-only internal surface probe`. API31 and API36 require separate descriptors. Binder, Hook and Root/Xposed are not needed. Hidden/internal access is needed only to attempt observation and may legitimately fail closed. P1 and P2 remain unauthorized.
