# ADR-0014: M7 Guest Service Virtualization

M7 routes explicit Guest `startService`/`bindService` calls through the existing M6 IAM proxy. `VirtualServiceRuntime` owns `(package, instance, component)` records and allocates one physical StubService per active Guest Service, preserving physical AMS tokens while restoring Guest `ServiceInfo` and intents at ActivityThread service transactions. `IServiceConnection` is wrapped at the Binder boundary so callbacks expose the Guest ComponentName and the Guest Binder returned by the real Service.

The Guest Service is created by Android `ActivityThread.handleCreateService`; no host wrapper invokes lifecycle callbacks. Notification identity translation is limited to physical package ownership required by foreground-service setup. Remote-process Service remains deferred to M10; Notification virtualization, Receiver/Provider, Alarm/Job, and background-policy bypass remain outside M7.
