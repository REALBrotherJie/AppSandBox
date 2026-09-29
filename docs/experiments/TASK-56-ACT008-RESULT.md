# Task-56 ACT-008 Process-Bounded Guest Application Session

Date: 2026-09-29
Status: CONFIRMED

- Debug-only Guest Application sessions now execute in the Host-owned `:guest_runtime` process behind a private ordinary Messenger protocol.
- The runtime coordinator exclusively owns live handles, recovery, registry writes and deletion arbitration. Host Workspace/Main use bounded asynchronous IPC and exact persisted run IDs.
- The protocol covers start, exact-run read, stop, restart, delete and debug runtime termination with versioned, bounded request/reply fields and request-ledger replay protection.
- Runtime startup recovers interrupted STARTING/RUNNING sessions as `CRASH_RECOVERY`; constructor and `onCreate` failures remain explicit and do not leak live handles.
- A deterministic blocking Guest fixture self-terminates the runtime during STARTING. The debug runner rebinds and reads the exact run after Binder death.
- Final immutable artifacts: `build/reports/task56/freeze-e7644c35eaf5/` from commit `e7644c35eaf5`.
- Host SHA-256: `600B56CED1FAD8C439C7370FDF21313387472056E3272ECB9425D6117415769C`.
- Guest SHA-256: normal `C8B0CD3A...A81E`, throwing `49C26C3D...53CF`, constructor crash `6419DCBA...5965`, blocking `38D1FD87...32A4`.

## Device Matrix

- API31 device `7b670025`: PASS, run `26c2dbd5f63f4a5d9bd9b0d3ecddff6f`; Host PID `26823` stayed stable and runtime PID changed `27199 -> 28434`.
- API36 device `emulator-5554`: PASS, run `f341769d8ca84310b5943233511bcfea`; Host PID `26646` stayed stable and runtime PID changed `26708 -> 26875`.
- Both APIs passed exact restart/read, runtime death recovery, two STARTING death recoveries, constructor/onCreate failures, A/B persistence and delete isolation.
- Both APIs confirmed the Guest package remained uninstalled, no Guest ActivityRecord existed, and Activity attach/lifecycle counts stayed zero.
- API31 real UI smoke passed through MainActivity -> Workspace: controls were visible; start/stop/restart produced RUNNING/STOPPED/RUNNING and restart rotated the run ID without crashing the Host UI.

## Build And Scope

- `:app:testDebugUnitTest`, debug/release assembly, all four Guest variants and `git diff --check` passed.
- The release manifest contains no ACT-008 service or runner and release code cannot execute the Guest Application callback.
- This confirms a process boundary only. The runtime remains Host-owned and does not provide Guest UID/package identity, system `bindApplication`, Guest components, ActivityRecord, task/window/token or framework Activity lifecycle.
- No hidden API, Activity attach, Binder/ServiceManager interception, hook, native injection or whole-package force-stop evidence was used.
