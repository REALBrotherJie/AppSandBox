# TASK-27 Public API Host Activity + Guest View/Delegate Multi-Instance MVP

日期：2026-09-26

## MVP status

`CONFIRMED API31/API36`。Debug demo creates two persistent logical instances from one verified immutable Guest revision, renders the Guest layout/resources inside a Host Activity, and stores independent counter/marker state.

## Instance registry

Production `GuestInstanceRecord` and `GuestInstanceStore` provide create/list/get/delete. The registry is schema-versioned and uses temp-file write, flush/sync, backup and replacement commit. Each instance has a UUID, Guest revision/package/APK/SHA reference, unique data root and timestamps. Invalid IDs, missing/unverified revisions, escaped roots, duplicate IDs and malformed registry data fail closed.

The Guest APK is referenced, not copied. Deleting an instance removes only its data root and registry entry; the Guest revision remains in `GuestStore`.

## API31/API36 validation

Devices `7b670025` (API31, arm64-v8a) and `emulator-5554` (API36, x86_64) passed the same two-instance workflow: shared revision/SHA, distinct IDs/roots, independent counters, restart recovery, reset/delete isolation and continued B operation. Raw output is not tracked; `scripts/task27-run.ps1` writes reproducible local reports under `build/reports/task27/<serial>/`.

## Visible Guest View/resources

The debug Activity displays Guest metadata, instance controls, persistent counter and Guest marker loaded through a per-instance controlled context. The visible system component remains `Task27DemoActivity`, a Host Activity.

## Isolation and boundaries

- A/B share Guest revision ID, APK path and SHA.
- A/B use different UUIDs, data roots, preferences files and marker files.
- Host default preferences/database are not used for Guest demo state.
- `pm path com.example.appsandbox.testguest` is empty on both devices.
- No Guest Activity object is constructed, attached or lifecycle-called.
- No ActivityThread, transaction, Binder, hidden API or Hook mechanism is used.
- This is a Host Activity + Guest View/Delegate demo, not Activity virtualization, a security boundary, a separate process, or arbitrary APK compatibility.

## Build and release

Debug/release builds and unit tests passed in task-27. `Task27DemoActivity`, task27 actions and debug runner code are debug-only; the Release merged manifest/APK contains no Task27 demo entry.

## Conclusion

```text
MVP status = CONFIRMED API31/API36
instance registry = create/list/get/delete
same Guest revision shared = YES
Guest View/resources visible = YES
Guest Activity attached/lifecycle = NO/NO
Guest system-installed = false
release debug entry excluded = YES
raw evidence tracked = 0
```
