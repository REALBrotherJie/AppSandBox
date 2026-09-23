# Task-15 API36 regression

## Devices and reproduction

API31: Xiaomi Mi 10, Android 12, arm64-v8a/armeabi-v7a/armeabi.
API36: sdk_gphone64_x86_64, Android 16, x86_64/arm64-v8a, PAGE_SIZE=4096.
AVD: Pixel_3a_API_36_extension_level_19_x86_64. The configured ext19 startup
remained offline waiting for metadata. Successful recovery used the installed
android-36/google_apis/x86_64 image with the same AVD hardware configuration,
an independent temporary data directory and fresh encryption-key image.
Original AVD data was not wiped. This is API36, not an ext19-specific claim.
See scripts/task15-emulator.ps1 and scripts/task15-run.ps1.

Each mode force-stops the Host, verifies runCount=1, captures PID and checks
pm path before/after. Regression code loads an explicit read-only copy without
changing GuestStore. The script checks imported SHA against the build APK.
Outputs: evidence/task15/7b670025 and evidence/task15/emulator-5554.

## Baseline regression (WS-1)

| Experiment | API31 | API36 | Difference |
|---|---|---|---|
| V-1 writable | PASS | SecurityException at loader construction | API36 requires read-only code |
| V-1 read-only | PASS | PASS | None in probe result |
| EXP-001 | CONFIRMED | CONFIRMED | Read-only experimental copy used |
| EXP-002 Option B/landscape | CONFIRMED_API31 | CONFIRMED_API36 | No observed semantic difference |
| EXP-003A C0 | CONFIRMED | CONFIRMED | Same known delegation gaps |
| EXP-003B0-P | Public observation completed | Public observation completed | Same wrapper/factory limitations |

B0 Activity comparison uses debug ExperimentActivity, not MainActivity;
the original task-14 MainActivity experiment remains a historical result.
Historical fixed SHA labels in old runners are now explicitly reference hashes;
current build/import verification is performed by the harness.
