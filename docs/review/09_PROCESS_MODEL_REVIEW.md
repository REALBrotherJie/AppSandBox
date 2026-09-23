# Process Model Review

## Comparison

| Model | Class/static isolation | Native isolation | Crash blast radius | Memory | Decision |
|---|---|---|---|---|---|
| Single host process | weak | weak | host-wide | best | experiments only |
| Per package process | medium | medium | package-wide | medium | possible later |
| Per instance process | strongest available | strongest available | instance | worst | preferred for untrusted execution |
| Fixed slot pool | slot-wide shared state | slot-wide | slot-wide | bounded | premature |

All ordinary host-created processes still share the host UID and SELinux domain. A process boundary is valuable for memory, static state, crash containment, and loader/native state, but it is not an OS-level guest sandbox.

## Slot pool review

The slot pool assumes that guests can safely reuse a process after complete cleanup. Class loaders, native libraries, threads, static singletons, Binder callbacks, WebView state, and system caches make reset difficult. It also complicates instance affinity and crash recovery.

## Recommendation

Do not retain Process Slot Pool in the early roadmap. Use one host process for non-executable metadata experiments, then evaluate one sandbox process per active guest instance. Revisit package sharing only after measurements.

## Key question

Running two unrelated APKs concurrently in one process is technically interesting but not necessarily a product requirement. MVP should support one active instance at a time if that produces a clearer and safer contract.
