# Task-55 ACT-007 API31 Guest Application Session

Date: 2026-09-28
Status: CONFIRMED

- Added a debug-only API31 Guest Application session controller and Host automation route.
- Sessions resolve a real GuestInstance/Guest revision, construct through the independent Guest `DexClassLoader` and C1 controlled context, then call only `Application.onCreate` through `Instrumentation.callApplicationOnCreate`.
- API31 device `7b670025`: two instances started with distinct data roots; stop and restart succeeded; an Application whose `onCreate` throws was rejected with attempted=1/completed=0.
- Host session status reports RUNNING/STOPPED independently per instance. Stopping removes the in-memory Application session without deleting instance data.
- Guest package remained uninstalled and `dumpsys activity` contained no Guest ActivityRecord.
- No Activity attach, Guest Activity lifecycle, hidden API, Binder interception, Hook, native or production runtime changes were made.
- Focused JVM, Debug, Release and `git diff --check` gates passed. Raw reports are under ignored `build/reports/task55/`.
