# TASK-23 ACT-004 Stub Transaction to Guest Activity Substitution Design

日期：2026-09-25

## Question

After a Host Stub receives a real system ActivityRecord, Task, token and Window, is there an API31/API36 client insertion point that can replace the soon-to-be-created Host Activity object with a Guest Activity object while preserving Host carrier state and explicitly mapping Guest logical state?

## Current evidence

- ACT-001 confirms Guest View/resource loading in a Host Activity on API31/API36.
- ACT-002 confirms a Guest `Activity` Java object can be constructed by a Guest `DexClassLoader` and public constructor/`Instrumentation.newActivity`; it is unattached, has no Window/application/Intent base linkage, and is not system-managed.
- ACT-003 confirms a debug Manifest Stub receives Host task/window/token state on API31/API36. `dumpsys` sees only the Host Stub component and Host ActivityRecord.

These results establish L0, L2-object construction and the Host carrier baseline. They do not establish L3.

## API31 launch chain

```text
Host Stub Intent                         [client input]
 -> Instrumentation.execStartActivity    [client; caller metadata]
 -> ActivityTaskManager/ATMS Binder      [boundary/system_server]
 -> ActivityStarter                       [system_server; resolve/policy]
 -> ActivityRecord + Task + token         [system_server; Host component]
 -> ClientTransaction                     [system_server -> client]
 -> LaunchActivityItem                    [client transaction item]
 -> ActivityThread/TransactionExecutor    [client internal]
 -> performLaunchActivity                 [client internal]
 -> LoadedApk + ContextImpl.createActivityContext [client internal]
 -> Instrumentation.newActivity           [public factory call]
 -> Activity.attach                       [framework internal binding]
 -> Window + lifecycle/onCreate           [client framework/system-managed]
```

`ActivityInfo` at ATMS resolution is the Host Stub's installed manifest record. The Guest class is safe to load only after the production Guest artifact has passed integrity verification and the logical mapping identifies a Guest revision. Loading the class earlier is possible technically, but does not create system identity. The Guest object remains unattached until the framework's attach phase; public code cannot insert it into the framework's transaction by simply calling `newActivity`.

The point at which public-only replacement stops is before/around the internal transaction-to-ActivityThread path. Public `Instrumentation.newActivity(ClassLoader, String, Intent)` creates an object, but the official API states that the object is locally usable while missing linkages required for system use. It does not cause `ActivityThread` to use that object, does not replace the `ActivityClientRecord`, and does not bind a token/window.

## API36 comparison

### Stable concepts

| Concept | API31/API36 design meaning |
|---|---|
| ATMS/PMS resolution | system_server selects an installed Host component and policy |
| ActivityRecord/Task/token | system-owned Host identity and task/window authority |
| ClientTransaction/LaunchActivityItem | system-approved launch delivery to the client process |
| ActivityThread transaction execution | client framework creates and attaches the Activity |
| Instrumentation.newActivity | public construction hook, not a substitution contract |
| Activity.attach | framework binding point for context/application/intent/info/window |
| Guest logical record | app-owned mapping, never a system record by itself |

### Version-sensitive internals

| Area | API31 | API36 | Risk |
|---|---|---|---|
| `ActivityClientRecord` | hidden client record fields and callbacks | fields/callback details evolve | adapter must be per-release |
| `ClientTransaction` | hidden transaction container | same concept, internal shape changes | no stable public interception point |
| `LaunchActivityItem` | hidden launch item | hidden item and parcel/state details evolve | old message recipes do not generalize |
| `TransactionExecutor` | internal callback executor | lifecycle/config/back behavior evolves | ordering is not an app API |
| `ActivityThread` | internal launch coordinator | implementation and state fields evolve | high maintenance/OEM sensitivity |
| `ContextImpl.createActivityContext` | internal Activity context construction | same concept, signature/state sensitive | Guest Context cannot be assumed equivalent |
| configuration/display callbacks | token/display/override configuration | more display/window/back state | Host carrier state must remain authoritative |
| predictive back | earlier integration | API33+ dispatcher behavior present | Guest mapping must not claim Host back identity |

API36 therefore does not provide a public substitution API. The modern design must treat `ActivityThread`, transaction items and context internals as version-specific research targets, not shared public mechanisms. Non-SDK access has been restricted since API28 and the restricted lists evolve by release. citeturn0search0turn0search2turn0search9

## Candidate insertion points

| Candidate | Can solve | Cannot solve | Classification | API31/API36 | Hook/hidden | Host token/window | Guest lifecycle |
|---|---|---|---|---|---|---|---|
| A `Instrumentation.newActivity` only | construct Guest object | make ActivityThread use it; bind Context/Window/token | PUBLIC | works as ACT-002 only | NO/NO | preserved only on separate Host object | NO |
| B `Instrumentation.execStartActivity` only | observe/translate outgoing Intent before ATMS | create Guest object or Guest attach; system still needs installed component | PUBLIC method, policy boundary | Host Stub translation possible | NO initially | Host Stub if translated | Host only |
| C Handler/message interception | observe legacy client messages | modern transaction ownership, stable API, complete state | INTERNAL/HOOK | fragile | YES/LIKELY | possible carrier, unproven | unproven |
| D ClientTransaction/LaunchActivityItem interception | target modern launch payload and restore mapping | public access, stable constructors, OEM/version stability | HIDDEN/INTERNAL | candidate | YES | can preserve Host token if exact state retained | unproven |
| E `ActivityThread.performLaunchActivity` interception | last-mile object/context selection | public access and rollback after partial setup | INTERNAL | candidate, highly sensitive | YES | candidate, must retain Host carrier state | unproven |
| F LoadedApk/ContextImpl substitution | improve Guest resources/context/application linkage | system ActivityRecord/Task identity and all policy | HIDDEN/INTERNAL | candidate, high maintenance | Host token only if separately preserved | unproven |
| G Stub + logical restore without framework substitution | durable mapping, failure handling, diagnostics | Guest cannot become framework Activity | PUBLIC | feasible negative gate | NO | Host only | Guest callbacks only if manual, not system-driven |

The required limitation is explicit: `Instrumentation.newActivity` can construct a Guest object, but alone it does not make `ActivityThread` treat that object as the normal framework Activity.

## Host/Guest/bridge state model

| Layer | State | Ownership/meaning |
|---|---|---|
| System Host state | `ActivityRecord`, `Task`, Host `ActivityInfo`, Host token, Host Window | system_server/client framework; real carrier identity |
| Logical Guest state | package/component, revisionId, Guest `ActivityInfo`, original Intent, instanceId | host-owned registry/runtime mapping; not a system record |
| Client bridge state | launchId, mapping phase, lifecycle phase, failure state | host runtime state; must be durable enough for recovery |

Permitted logical mappings:

- Host token -> carrier token reference only.
- Host Window -> carrier Window reference only.
- Host ActivityInfo -> system launch contract.
- Guest ActivityInfo -> logical metadata for future client behavior.
- Host taskId -> Host taskId only; never relabeled Guest taskId.
- Host lifecycle -> Host lifecycle only until substitution is proven.

## Failure and rollback design

| Failure | Required behavior |
|---|---|
| Guest class missing | mark restore failure; keep Guest uninstalled; finish Host Stub cleanly |
| Guest class not Activity | reject mapping; no attach/window mutation |
| Guest constructor throws | catch at bridge boundary; preserve Host process; finish or fallback |
| Guest resources unavailable | do not partially replace context; retain/finish Host carrier |
| logical mapping missing | fail closed; never guess package/component |
| API-specific shape mismatch | abort before attach; record version/phase |
| Host Stub launched but restore fails | Host remains a Host record; show controlled error or finish |
| any partial attach attempt | future experiment must define atomicity/rollback; ACT-004A-POSITIVE must abort |

The first positive experiment must have explicit phases: `RECEIVED`, `MAPPED`, `CLASS_LOADED`, `OBJECT_CREATED`, `ATTACH_PENDING`, `ATTACHED`, `FAILED`. No failure may leave a Guest object presented as attached when attach did not complete.

## API/security boundary

| API/state | Classification for ordinary app |
|---|---|
| `Instrumentation.newActivity(ClassLoader, String, Intent)` | PUBLIC; object factory only |
| `Instrumentation.execStartActivity` | PUBLIC method, but system identity/policy remains external |
| `Activity.attach` | INTERNAL/non-SDK for this design; NOT AUTHORIZED |
| `ActivityThread` | INTERNAL; NOT AUTHORIZED |
| `ClientTransaction` | HIDDEN/INTERNAL; NOT AUTHORIZED |
| `LaunchActivityItem` | HIDDEN/INTERNAL; NOT AUTHORIZED |
| `LoadedApk` | INTERNAL; NOT AUTHORIZED |
| `ContextImpl.createActivityContext` | INTERNAL/non-SDK; NOT AUTHORIZED |
| `Window` token | public token observation only; value is system-owned |
| `ActivityInfo` | public data type, but Host record comes from system; Guest copy is logical only |

Android documents non-SDK interfaces as implementation details subject to change; blocklisted access can fail through linkage or reflection errors. This is why a design result cannot be treated as implementation authorization. citeturn0search0turn0search1

## ACT-004A negative public-only design

### Question

After a Host Stub starts, can public-only code correlate a verified Guest object construction result with the Host Stub without substituting the framework Activity?

### Hypothesis

It can create a logical correlation record and a separate unattached Guest object, but cannot replace the Host Activity object or produce Guest system lifecycle/token/window semantics.

### Scope

API31/API36; existing Host Stub; primitive `launchId`; production GuestStore verification; no Guest object inserted into the Stub launch; no attach/lifecycle/window/framework interception.

### Required technology

Public Activity/Intent/PackageManager APIs, existing GuestStore/verifier, Guest `DexClassLoader`, a debug-only logical record and evidence logging.

### Forbidden technology

Activity.attach, ActivityThread, ClientTransaction, LaunchActivityItem, internal reflection, Hook, Binder, hidden API, Stub pool and Guest lifecycle calls.

### Success criteria

- Host Stub remains the only system Activity.
- logical record contains launchId, Host component/task and Guest revision/component.
- optional Guest object remains explicitly `UNATTACHED`.
- Host token/window/task are unchanged and labeled Host.
- failures do not install Guest or crash Host.

### Negative controls

Direct uninstalled Guest launch, missing Guest class, non-Activity class, malformed/missing launchId, and comparison against ACT-003 Host-only behavior.

### Observable evidence

Host component/task/window/token, logical mapping phase, Guest class loader identity, object attached flag, Guest install state, lifecycle log and process survival.

### Abort criteria

Any attempt to call attach/lifecycle, any token/window relabeling, any Guest ActivityRecord claim, Host crash, or any need for hidden/internal API. Such a result becomes a design blocker, not a partial success claim.

## ACT-004A positive design, deferred

### Question

Can a future controlled non-public client adapter restore Guest Activity state at the transaction boundary while retaining Host carrier token/window/task state?

### Required technology

A separately authorized API31/API36 version adapter around ClientTransaction/ActivityThread or an equivalent controlled mechanism, with explicit rollback instrumentation and no production deployment assumption.

### Success criteria

Only after separate authorization: Guest class selected from verified revision; Host token/window/task retained; Guest context/application/resource contract measured; lifecycle ordering and failure rollback observed; Host system record remains truthfully Host.

### Not allowed to claim from design

No attached Guest Activity, real Guest task, Guest ActivityRecord, Guest Window identity, or complete lifecycle until experimentally observed on both APIs.

## Preferred route and open questions

Preferred route: `Route G`, staged as `ACT-004A-NEGATIVE` first, then a separately authorized `ACT-004A-POSITIVE` using a version-specific client bridge. It is preferred because it keeps the already-proven Host carrier and makes logical/system identity explicit. Route A remains the fallback.

ACT-004A must falsify whether public-only logic can do more than correlation, and whether the chosen transaction boundary can preserve Host state without inconsistent Guest Context/LoadedApk/ActivityInfo. Still unproven: attach atomicity, Guest Application, ActivityInfo substitution, lifecycle/result/back behavior, process death, saved state, configuration, predictive back, permissions, recents and OEM variance.

Deferred: Binder translation, Stub pools, launchMode mapping, Guest registry expansion, Guest-to-Guest navigation, external resolver, split Activities and production carrier implementation.

## Scope compliance and conclusion

```text
ACT-004 design = READY
Guest substitution implemented = NO
Guest Activity attached = NO
Guest lifecycle executed = NO
Host token/window remains Host-owned = YES
Binder/Hook/hidden API used = NO
Ready for ACT-004A negative public-only experiment = YES
Ready for ACT-004A positive substitution experiment = NO
```

Modified docs only:

```text
docs/design/ACTIVITY_SUBSTITUTION_DECISION.md
docs/experiments/TASK-23-ACT004-SUBSTITUTION-DESIGN-RESULT.md
```

No task-18 through task-22 raw evidence was modified or regenerated. ACT-004A is not executed by this task.
