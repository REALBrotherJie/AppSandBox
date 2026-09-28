# Task-55 ACT-007 API36 Guest Application Session

Date: 2026-09-28
Status: CONFIRMED

- Debug-only API36 runner creates Guest Application objects from production GuestStore revisions using an independent DexClassLoader and controlled per-instance Context.
- Only `Application.onCreate` is invoked through public Instrumentation APIs. No Activity attach/lifecycle, hidden API, Binder interception, hook or native path is used.
- Matrix covers start, stop, restart, throwing onCreate and two simultaneously active isolated instances.
- Host Workspace displays the persisted per-instance Application session state.
- API36 `emulator-5554` confirmed real Guest `Application.onCreate`, restart, throwing Guest handling and distinct instance data roots.
- Device inspection confirmed the Guest package remained uninstalled and no Guest ActivityRecord existed; Host Workspace remained the resumed Activity.
