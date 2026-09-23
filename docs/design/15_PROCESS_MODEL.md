# Process Model

| Option | Memory | Isolation | Crash impact | Compatibility |
|---|---|---|---|---|
| A host process | best | weakest | host crash | easiest initially |
| B shared sandbox process | good | medium | multiple guests affected | medium |
| C process per instance | worst | best available to host | contained to instance | hardest |
| D fixed process slot pool | bounded | medium | slot-level | complex scheduling |

All options share the host UID and SELinux domain unless the OS itself participates. A process boundary improves failure containment and class/static-state separation; it does not provide a unique Android app sandbox.

`PROPOSED`: M0-M2 host process for parsing and deterministic experiments; introduce a sandbox process before arbitrary guest Application/component execution; evaluate per-instance processes only after measuring memory and lifecycle cost. Process slots are a later optimization, not a starting abstraction.
