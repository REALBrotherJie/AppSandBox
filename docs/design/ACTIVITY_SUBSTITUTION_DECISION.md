# Activity Substitution Decision

状态：DESIGN GATE ONLY，2026-09-25。没有实现 Guest substitution，也没有授权 ActivityThread/ClientTransaction interception。

## Decision

`Route G` remains the preferred candidate for a future ACT-004A-positive experiment:

```text
Host Stub launch
 -> system creates Host ActivityRecord/Task/token/Window
 -> client observes a version-specific launch boundary
 -> logical Guest record is restored by launchId
 -> future experiment may construct/attach a Guest object under controlled conditions
```

Route G is a candidate, not an implementation authorization. Route A Host Activity + Guest View remains the stable fallback. Route D alone is insufficient; Route E is the likely client bridge but has the highest API sensitivity.

## Hard boundary

The Host token, Host Window and Host ActivityInfo remain system-owned Host state. A Guest `ActivityInfo` is logical metadata until the system itself resolves a Guest package. A Host `taskId` cannot be renamed as a Guest taskId. A constructed Guest Java object is not system-driven until a future experiment proves a complete attach/transaction path.

## First experiment decision

The first follow-up should be `ACT-004A-NEGATIVE`, public-only and no substitution. It must demonstrate the ceiling: a Host Stub can be associated with a logical Guest record, but public APIs cannot replace the framework-created Host object or make the Guest object participate in the system lifecycle. `ACT-004A-POSITIVE` is deferred until a separately approved non-public mechanism and rollback plan exist.

## Why not Binder first

Binder translation can change a start request, but it does not by itself solve client Activity construction, Activity-specific ContextImpl, LoadedApk, attach, or Window binding. Binder is therefore optional for the first negative gate and deferred for broader translation.
