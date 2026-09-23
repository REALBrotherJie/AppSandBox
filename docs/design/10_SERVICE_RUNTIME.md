# Service Runtime Design

## Native behavior

`startService`, `bindService`, and `stopService` are mediated by AMS and `ActiveServices`; client-side creation and callbacks occur through ActivityThread transactions. Foreground services are subject to notification and background-start policy.

## Proposed model

`GuestServiceController` keeps logical service records keyed by instance and component. It maps start IDs, binding tokens, `ServiceConnection` callbacks, and stop semantics. It must reject calls after instance teardown and serialize lifecycle transitions.

Options:

- A: execute Service objects in a sandbox process under a host-owned controller.
- B: represent services as host services and dispatch logical calls.
- C: support only in-process logical services initially.

`PROPOSED`: start with C for experiments, then A if isolation is required. B has the strongest system integration but conflates host and guest service identity.

Android 8+ background execution, Android 12+ notification/foreground-start restrictions, and Android 14+ foreground-service type/permission rules make system-backed behavior version-sensitive. Exact support must be a matrix, not a blanket promise.
