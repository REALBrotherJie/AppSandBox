# Multi-instance Review

## Definitions

Multi-instance means independent logical data/runtime state, not necessarily simultaneous execution.

### MVP

- same immutable package revision;
- instance A and B have separate files, databases, preferences, cache, permissions, and logical state;
- instances may run at different times;
- no claim of concurrent same-process isolation.

### Advanced

- simultaneous instances;
- separate process per instance;
- independent runtime lifecycle;
- defined account, notification, provider, native, and task semantics.

## Findings

Filesystem separation is necessary but insufficient. Application singletons, class statics, native globals, process services, thread pools, and system attribution can cross instance boundaries in one process.

`PROPOSED`: implement package/instance data modeling before concurrent runtime. Treat sequential instance switching as the first meaningful multi-instance milestone. Only advertise concurrent isolation after the process experiment passes.
