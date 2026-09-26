# TASK-27 Public API Host Activity + Guest View/Delegate Multi-Instance MVP

日期：2026-09-26

## MVP status

`CONFIRMED API31/API36`。Debug demo creates two persistent logical instances from one verified immutable Guest revision, renders the same Guest layout/resources inside a Host Activity, and stores independent counter/marker state.

## Instance registry

Production `GuestInstanceRecord` and `GuestInstanceStore` provide create/list/get/delete. The registry uses a schema-versioned JSON file with temp-file write, flush/sync, backup and rename commit. Each instance has a UUID, Guest revision/package/APK/SHA reference, unique data root and timestamps. Invalid IDs, missing/unverified revisions, escaped roots, duplicate IDs and malformed registry data fail closed with explicit store states.

The Guest APK is referenced, not copied. Deleting an instance removes only its data root and registry entry; the Guest revision remains in `GuestStore`.

## API31 validation

Device `7b670025`, API31, arm64-v8a, page size 4096. Two instances shared the same revision and APK SHA, while IDs and roots differed. A/B reached counter 2 independently, survived force-stop/relaunch, reset A left B at 2, delete A left the remaining B record at 2, and B incremented to 3. Screenshot and raw evidence are under `docs/experiments/evidence/task27/7b670025/`.

## API36 validation

Device `emulator-5554`, API36, x86_64, page size 4096. The same workflow passed with shared revision/SHA, distinct roots, restart recovery, reset/delete isolation and continued B increment. Evidence is under `docs/experiments/evidence/task27/emulator-5554/`.

## Visible Guest View/resources

The debug Activity displays Guest metadata, A/B selector buttons, increment/reset/delete controls, the persistent counter and the Guest `exp002_test_layout` marker loaded through a per-instance `Exp003c1ControlledContext`. The screenshot shows the Guest resource marker and the selected instance root. The visible system component remains `Task27DemoActivity`, a Host Activity.

## Isolation and boundaries

- A/B share Guest revision ID, APK path and SHA.
- A/B use different UUIDs, data roots, preferences files and marker files.
- Host default preferences/database are not used for Guest demo state.
- `pm path com.example.appsandbox.testguest` is empty on both devices.
- No Guest Activity object is constructed, attached or lifecycle-called.
- No ActivityThread, transaction, Binder, hidden API or Hook mechanism is used.
- This is a Host Activity + Guest View/Delegate demo, not Guest Activity virtualization, a security boundary, a separate process, or arbitrary APK compatibility.

## Build and release

```text
:app:testDebugUnitTest = PASS
:app:assembleDebug = PASS
:app:assembleRelease = PASS
:test-guests:GuestTestApp:assembleDebug = PASS
git diff --check = PASS
```

The instance model/store is production-buildable. `Task27DemoActivity`, task27 actions and debug runner code are debug-only; the Release merged manifest/APK contains no Task27 demo entry.

## Modified files

```text
app/src/main/java/com/example/appsandbox/model/GuestInstanceRecord.kt
app/src/main/java/com/example/appsandbox/storage/GuestInstanceStore.kt
app/src/test/java/com/example/appsandbox/storage/GuestInstanceStoreModelTest.kt
app/src/debug/AndroidManifest.xml
app/src/debug/java/com/example/appsandbox/experiments/task27/Task27DemoActivity.kt
scripts/task27-run.ps1
docs/experiments/evidence/task27/<serial>/
docs/experiments/TASK-27-MULTI-INSTANCE-MVP-RESULT.md
```

## Conclusion

```text
MVP status = CONFIRMED API31/API36
instance registry = create/list/get/delete with atomic schema registry
same Guest revision shared = YES
Guest View/resources visible = YES
Guest Activity attached/lifecycle = NO/NO
Guest system-installed = false
release debug entry excluded = YES
```
