# Binder Virtualization Core

`VirtualBinderManager` installs adapters before `LoadedApk`, `Application`, or Guest code can cache framework services. The call path is `BinderCallContext -> IdentityPolicy -> BinderServiceAdapter -> MethodPolicyRegistry -> physical interface`; results may instead route to VPM or VirtualActivityManager.

Identity is the M2 `RuntimeIdentity`: Guest package/virtual UID remain logical values for Guest-facing queries, while system-server validation receives Host package/Linux UID. Rewrites are method/position aware. `AttributionSource` chains are rebuilt with physical identity. Provider `asBinder()` returns an `IBinder` facade whose `queryLocalInterface()` preserves the provider adapter after framework re-resolution.

Current adapters: `package` (`ActivityThread.sPackageManager`, VPM routing/result overlay), `activity` (`ActivityManager.IActivityManagerSingleton`, provider acquisition and permission policy), `activity_task` (`ActivityTaskManager.IActivityTaskManagerSingleton`, M4 intent routing), `appops` (`AppOpsManager.mService`, package/UID positions), and the provider identity boundary. Unknown methods default to diagnosed physical passthrough; explicit block is available and tested. Object methods and underlying exceptions retain defined semantics.

API31 and API36 use the same probed singleton/cache members. API31 additionally exposes MIUI AMS/ATMS extension methods, which remain diagnosed passthrough. `SERVICE_MANAGER_INTERCEPTION = NOT_CURRENTLY_REQUIRED`: replacing framework singletons/caches before Guest creation covers the current services. Raw Binder interception is not used; provider binder locality is preserved by the Java `IBinder` facade.

Previous production hooks in `GuestRuntimeClassLoader` and `SystemIdentityBridge.wrapContentProvider` were removed. Remaining non-core interception is `ActivityLaunchInterceptor`, which operates on client transactions rather than a system-service Binder boundary.
