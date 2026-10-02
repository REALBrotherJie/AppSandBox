# M12 System-Service Integration Architecture

## Decision

Select `HYBRID_SYSTEM_SERVICE_ADAPTERS`. Keep the five existing M12 registries and the M10 Coordinator as the only logical identity/process owners. The four services may use different client plumbing, but all persistent mappings use package revision + Guest package + instance and all asynchronous delivery uses `VirtualProcessKey` plus binding generation.

## Existing Foundation

`VirtualPermissionManager` owns Guest grant/deny/rationale state and is manifest-gated. `VirtualPendingIntentRegistry` owns logical type/request-code/Intent identity and generation-bearing physical tokens. `VirtualNotificationRegistry`, `VirtualAlarmRegistry`, and `VirtualJobRegistry` own instance-scoped logical records and are cleaned by `VirtualInstanceDeletionManager`. The registries are thread-safe in-memory maps today; persistent reconciliation is still required for production.

## Permission

Use a hybrid: virtual checks through the existing VPM/M6 package and activity Binder paths; requests through an AppSandbox-owned broker before PermissionController. The broker returns the logical Guest request code and calls the real Guest Activity callback. Host physical grants remain a separate capability gate. M11 FIXED/ROUTE may use virtual location permission; REAL_PASSTHROUGH must require Host location capability. No API31/API36 Guest request callback probe exists in the current fixture, so this decision is selected but not runtime-proven.

## PendingIntent

Create installed Host `StubPendingActivity`, `StubPendingService`, and `StubPendingReceiver` components. Physical PendingIntents carry only a stable mapping token; serialized logical Intent data is stored in the registry with package revision validation. A stub resolves token -> registry -> M10 Coordinator -> current process generation. Deleted/revised records are rejected. Guest extras with arbitrary ClassLoaders never cross the physical PendingIntent boundary.

## Notification

Intercept `INotificationManager` from the Guest process after framework service cache invalidation. Transform Host package/opPackage, namespaced physical channel, tag/id, and all content/delete/action PendingIntents. Query methods reverse-map channel metadata. Posting and tap delivery use the shared PendingIntent stubs; Host `POST_NOTIFICATIONS` remains an independent capability. Current adapter only rewrites package identity, so channel/post/cold-start are not yet proven.

## Alarm

Intercept `IAlarmManager` and call the real Host `AlarmManager` with transformed Host package and shared physical PendingIntent. Preserve RTC versus elapsed bases. Exact calls require Guest virtual capability AND Host `canScheduleExactAlarms`; denial is not silently downgraded. Stub delivery resolves the alarm record and cold-starts through M10. No private timer is part of the design.

## Job

Intercept `IJobScheduler`; transform Guest `JobInfo` to collision-safe Host job ID and installed `StubJobService`. Persist Guest JobService component and supported JobInfo fields. Stub `onStartJob` creates an execution session containing host job ID, Guest key, VirtualProcessKey and generation, then routes through M10. `jobFinished` and `onStopJob` use the session to call the physical `IJobCallback`; Guest `cancelAll` iterates only this instance's records and never calls Host `cancelAll`.

## API Boundaries and Evidence

API31 device `7b670025` is SDK 31/arm64-v8a; API36 device `emulator-5554` is SDK 36/x86_64. `dumpsys notification`, `dumpsys alarm`, and `dumpsys jobscheduler` were captured under `build/reports/m12-arch/`. Both platforms expose the physical services, but this checkout has no Guest probe fixture or installed StubJobService/PendingIntent components; therefore Permission callback, PendingIntent cold start, real Notification post/tap, real Alarm delivery, and real Guest JobService completion remain unproven on both APIs.

## Rejected Alternatives

An independent process manager per service is rejected because it would split M10 generation ownership. Passing Guest package names directly to system_server is rejected by physical UID/package validation. In-process timers are rejected for Alarm semantics. Direct Host `cancelAll()` is rejected for Job isolation. Full M12 productionization is outside this review.

## Remaining Implementation

Add the four Host stub components, shared token persistence/reconciliation, client Binder proxies, permission broker callback bridge, Notification object transformation, real Alarm scheduling, and StubJobService/IJobCallback bridge. Then run the API31/API36 proof matrices and current-head M2-M11 regression in the next planner-authorized implementation task.
