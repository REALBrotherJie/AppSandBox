# TASK-17 Activity Runtime / Carrier Architecture Result

日期：2026-09-24  
状态：READY FOR DESIGN REVIEW；本轮未实现 Activity runtime。

## A. Activity Design Status

`READY`

## B. Public-only Ceiling

`Level = L2 at most for an arbitrary uninstalled Guest Activity object; L0/L1 are the stable usable ceiling.`

Reason: public `Instrumentation.newActivity`/`AppComponentFactory` can instantiate a class, but public APIs do not provide an accepted system Activity token, Guest ActivityInfo/ActivityRecord, Activity-specific Guest ContextImpl or system task identity.

## C. Android 12 Launch Chain

`Context.startActivity -> Instrumentation.execStartActivity -> ATMS Binder -> ATMS/ActivityStarter -> ActivityRecord/Task/token -> ClientTransaction/LaunchActivityItem -> ActivityThread.performLaunchActivity -> ContextImpl.createActivityContext -> newActivity -> Activity.attach -> Window -> onCreate`.

详见 `docs/research/activity/01_ANDROID12_ACTIVITY_LAUNCH_CHAIN.md`。

## D. Android 16 Differences

概念链稳定；`ActivityClientRecord`、transaction execution、configuration/display、predictive back、hidden signatures and callback details are version-sensitive. Old `H.LAUNCH_ACTIVITY` recipes are not an API31-36 design basis.

## E. System vs Client State

`ActivityRecord`, `Task`, package resolution and lifecycle policy are system_server-owned. Token/window are hybrid. `LoadedApk`, `ContextImpl`, `Window` object, `Application`, `Instrumentation` and Activity object are client-owned. `ActivityInfo`, `Intent`, configuration, saved state and result routing are hybrid.

## F. Route Comparison

| Route | Conclusion |
|---|---|
| A | Public, stable L0 fallback |
| B | Public delegate, L1 only |
| C | Stub substitution, first token candidate |
| D | Instrumentation interception, partial client substitution |
| E | ActivityThread/ClientTransaction interception, version-sensitive bridge |
| F | Binder proxy translates requests but cannot create Guest Activity |
| G | Stub + client restore + optional Binder, preferred high-compat candidate |
| H | LoadedApk/ContextImpl integration, high fidelity/high maintenance |
| I | Xposed-style interception, broad but deployment constrained |
| J | Native/seccomp mediation, not sufficient for Java Activity attach |

## G. Preferred Route

`PROPOSED: Route G staged as C -> E -> G`.

Guest Intent -> virtual resolver -> VirtualActivityRecord/launch id -> Host Stub Intent -> ATMS creates Host record/token/window -> client restores Guest mapping -> Guest class/resource/context adapter -> lifecycle/Intent/result/task translation.

## H. Secondary Route

`PROPOSED: Route A Host Activity + Guest View`, as stable product fallback and negative control.

## I. Rejected Routes

Public-only direct Guest launch, Binder-only, native/seccomp-only and one universal Stub are rejected for the first experiment because each lacks either system resolution, Java object attach, or manifest-dependent policy fidelity.

## J. Stub Activity Requirements

At minimum separate standard/singleTop/singleTask/singleInstance or equivalent policy-compatible slots; additional dimensions may be required for translucent/floating, noHistory, orientation, theme/window flags, process and recents policy. Do not assume one Stub can encode all Guest manifest combinations.

## K. Manifest Attributes

Before Stub selection: launchMode, taskAffinity, exported/enabled, theme, screenOrientation, configChanges, resizeableActivity, supportsPictureInPicture, excludeFromRecents, noHistory, documentLaunchMode, process and translucency/floating semantics.

## L. Activity Context Strategy

ClassLoader: immutable Guest revision. Resources: Guest APK plus current configuration. Application: explicit controlled Guest Application or later LoadedApk integration. Storage: per-instance host-owned logical root. Theme: must be resolved before content creation. The current Controlled Context is not an Activity Context.

## M. Token / Window Strategy

A real token comes only from ATMS for a declared Host Stub. Guest Activity may reuse the carrier window/token only as a logical attachment; system_server still attributes window/task/orientation to Host Stub. Token mapping must be lifecycle-bound and recoverable.

## N. ActivityInfo Strategy

Host `ActivityInfo` is the system launch contract. Guest `ActivityInfo` is a logical registry object used by the client adapter. They must coexist and never be silently substituted in system_server. Current `GuestPackageReader` only exposes activity count; detailed ActivityInfo/filters/launchMode are `MISSING`.

## O. Intent Strategy

Keep original Guest Intent in a controlled launch record; send a minimal Host-safe Stub Intent with opaque launch id and primitive routing data. Avoid putting arbitrary Guest Parcelable into a system-visible extra. Restore with Guest ClassLoader only after client ownership is established. Preserve flags/categories/data/clip/referrer only after explicit compatibility tests.

## P. Task / Back Stack

System stack contains Stub records; logical stack contains Guest records. Every Stub needs a durable Guest mapping. Guest A -> Guest B maps to Stub A -> Stub B; back pops both mappings. Recents/task description/relaunch are Host-visible unless separately translated.

## Q. launchMode

Map `standard`, `singleTop`, `singleTask`, `singleInstance`, and `singleInstancePerTask` conservatively. `singleTop` needs `onNewIntent`; `singleTask`/affinity/CLEAR_TOP affect existing Stub task; `NEW_TASK`, `NEW_DOCUMENT` and `MULTIPLE_TASK` require independent experiments. Do not claim full support in the first version.

## R. Lifecycle

System drives Host Stub lifecycle. Client translation may drive Guest callbacks, but manual callback forwarding is delegation, not system-managed Guest lifecycle. `onNewIntent`, result, configuration and saved state need explicit mapping.

## S. Multi-instance

Use unique `instanceId`/launch id per Guest instance; share immutable revision bytes but isolate instance data and runtime state. Token map must not key only by package/component.

## T. Process Death

Persist launch id, Guest package/revision/component, Stub component, logical task id, serialized primitive Intent and state version before starting Stub. On process restart rebuild mappings from persistent records; Guest Parcelable state and in-memory token associations are not assumed recoverable.

## U. Package Registry Requirements

Current fields: package/version/path/label/component counts/revision/SHA/size/schema. Missing for Activity runtime: ActivityInfo records, intent filters, launchMode, theme, orientation, taskAffinity, exported/enabled, process, document/recents/window flags, split association and parser provenance. `GuestPackageReader` currently makes these fields `MISSING`; parser is not changed this round.

## V. Non-public Requirements

| Technology | Status |
|---|---|
| Hidden API | LIKELY REQUIRED for L3/L4 |
| Instrumentation | OPTIONAL for C; LIKELY for E/G |
| ActivityThread | REQUIRED for E; DEFERRED implementation |
| ClientTransaction | LIKELY for E/G |
| Binder | OPTIONAL early; likely later for broader translation |
| Hook | OPTIONAL early, likely for E/I |
| Native | DEFERRED; not required for first Java proof |

## W. Binder Timing

`Needed for first experiment = NO`. Stub token creation and a narrow client-side observation can first validate the carrier baseline. Binder interception is justified only if public/client interception cannot translate the outgoing Guest launch.

## X. Hook Timing

`Xposed-style / method hook needed early = MAYBE`. It is not required for Route A/C baseline; it may become necessary for E/G, but system/root deployment and API/OEM risk make it a compatibility tool rather than the first assumption.

## Y. Recommended Experiment Sequence

1. ACT-001 Host Activity + Guest View baseline.
2. ACT-002 Guest Activity Java object construction negative/positive controls.
3. ACT-003 Manifest Stub real token/task/window baseline.
4. ACT-004 Stub transaction to Guest Activity client substitution.
5. ACT-005 lifecycle and Activity Context contract.
6. ACT-006 Guest-to-Guest navigation/result/back stack.
7. ACT-007 launchMode/onNewIntent/affinity/flags.
8. ACT-008 process death/saved state/recovery.

## Z. First Experiment

`ACT-001 / Host Activity + Guest View baseline`.

Question: what Activity/task/window semantics are already available without Guest Activity substitution?  
Hypothesis: public Host Activity can render Guest resources/views, but system identity remains Host.  
Scope: one immutable test APK, one Host Activity, no hooks/Binder/hidden API.  
Technology: existing GuestStore, Guest ClassLoader/resource path, public View APIs.  
Success: Guest marker/theme/layout visible; Host package/component/token/task reported separately.  
Negative controls: direct uninstalled Guest launch and a writable/wrong revision.

## AA. External Sources

25 sources are registered in `docs/research/activity/SOURCES.md`, including API31/API36 AOSP `ActivityThread`, `ContextImpl`, `Activity`, `Instrumentation`, ATMS, ActivityStarter, ActivityRecord, LaunchActivityItem; Android Developers task/back stack, lifecycle, predictive back, Activity, Instrumentation, AppComponentFactory and non-SDK restrictions; VirtualApp, RePlugin, Shadow, VirtualAPK, DroidPlugin, VirtualXposed; and two virtualization papers.

## AB. Patent / Provenance Notes

Stub pools, component substitution, client transaction restoration, Binder service proxying and Xposed-style interception are patent/provenance review candidates. This document extracts public architecture patterns only, copies no implementation, and makes no infringement conclusion. Licenses and patent status require separate legal review.

## AC. Modified Files

Only `docs/` files are modified or added by task-17. No `app/src/`, `test-guests/` or `scripts/` changes.

## AD. Git

Final status is recorded after verification in the task response. Expected changes are docs-only and task-17 remains uncommitted until review.

## AE. Next Step

`Ready to execute first Activity experiment: NO`.

Reason: this round is design-only; ACT-001 is specified but must be explicitly started in a later task.
