# Activity Runtime Design

## Normal path

```text
Activity.startActivity
 -> Instrumentation
 -> ActivityTaskManager client
 -> system_server task/activity records
 -> process start or existing process transaction
 -> ActivityThread transaction
 -> performLaunchActivity
 -> Activity instance, attach, onCreate
```

The platform requires the target activity to be known to system package/task policy. An uninstalled guest is not.

## Options

### A: host-declared proxy activity

The host asks ATMS to launch a declared host activity, then maps the logical guest target inside that host surface.

Pros: uses public task/window machinery; compatible with ordinary lifecycle entry points. Cons: task identity, back stack, result, configuration, saved state, and system callbacks require translation; many guest assumptions see the host component.

### B: logical guest activity inside a host container

One host activity owns a guest activity state machine and view/window surface.

Pros: explicit control and fewer manifest slots. Cons: not a real Android Activity from the system's perspective; window, permission, task, menu, and lifecycle semantics diverge.

### C: platform integration/custom Android build

Modify framework/package management to register guest packages or provide a new system API.

Pros: closest to native semantics and true identity. Cons: requires OS/device control, signing/platform changes, and is outside a normal Play-distributed host app.

## Comparison

| Criterion | A | B | C |
|---|---:|---:|---:|
| Public API feasibility | medium | high | low for ordinary app |
| Native task fidelity | medium | low | high |
| Version sensitivity | high | medium | platform-owned |
| Security transparency | medium | high | requires OS security review |
| Implementation cost | high | medium | very high |

`PROPOSED`: investigate A first for a constrained, user-visible Activity subset, while keeping B as a fallback for APIs that cannot be represented. C is not a product assumption. This is not a final implementation decision.

Must experimentally cover launch modes, affinity, flags, result delivery, task/back behavior, saved state, orientation, theme/window, multi-window, configuration change, process death, and Android 9-16 behavior.
