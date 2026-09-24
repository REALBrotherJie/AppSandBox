# Task-15 final report

Date: 2026-09-24. All experimental framework calls use PUBLIC SDK APIs.
Reflection is restricted to Guest-owned classes, methods and test flags.
No Guest Activity/Service/Receiver/Provider was started. The only new Activity
is a debug Host experiment entry. No Hook, Binder interception, JNI or hidden
framework field observation. No push.

## A. Commits

| Workflow | Commit and message |
|---|---|
| WS-0 task-14 | f44378c feat(task-14): record EXP-003B0-P, B1 plan and V-1 registration |
| WS-0 source docs | cb0ceeb docs(task-15): preserve task specifications and external research |
| WS-0 audit | 18922f0 docs(WS-0): correct B0 delegation attribution and application binding semantics |
| WS-1 initial partial | 9ee2ac0 test(WS-1): add fresh-process public regression harness and record API31 baseline; API36 pending |
| WS-1 completion | 46c7650 test(WS-1): confirm API36 read-only code requirement and complete baseline regression |
| WS-2 | c3591d0 feat(WS-2): implement public C1 context and verify API31 storage and binding matrix |
| WS-3 | 0b1362c test(WS-3): verify Guest layouts theme and landscape through C1 on API31 |
| WS-4 | d6af4da test(WS-4): gate minimal Guest onCreate and verify persistence and exception containment on API31 |
| WS-5 | f915fb1 test(WS-5): observe shared and independent loader multi-instance behavior on API31 |
| WS-6 | 53bc38f test(WS-6): record API36 C1 layout onCreate and multi-instance regression |
| WS-7 | This report's commit: docs(WS-7): consolidate task-15 evidence, compatibility boundaries and proposed ADRs |

WS-1 initially remained PARTIAL while the existing AVD was offline. Independent
work continued; its follow-up commit completed API36 validation before WS-6.

## B. Devices

| Device | SDK / release | ABI | PAGE_SIZE |
|---|---|---|---|
| Mi 10 (7b670025) | 31 / 12 | arm64-v8a,armeabi-v7a,armeabi | 4096 |
| sdk_gphone64_x86_64 (emulator-5554) | 36 / 16 | x86_64,arm64-v8a | 4096 |

The existing Pixel_3a_API_36_extension_level_19_x86_64 AVD configuration was
used with the installed android-36 image and separate temporary experiment
disks after metadata startup failure. No original AVD wipe. No ext19 or 16KB
claim. Startup recipe: ../../scripts/task15-emulator.ps1.

## C. Guest APK

Final path: test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk.
Size=30356 bytes. Build SHA256 and both imported SHA256:
`666fbb1ec28d5c36e8dcde928e67bbd95fc31d9025beeaff1af3aace2b536494`.

| Device | GuestStore revision | SHA match | pm path before/after |
|---|---|---|---|
| API31 | 2c360be5-df37-4458-83d1-9c2fc1c24bbe | true | empty / empty |
| API36 | 934ad563-d21e-40ac-8be7-88bf5b1d7cfd | true | empty / empty |

Every rebuilt APK was imported as a new revision. Read-only copies are
experimental cache files, not silent replacements of GuestStore base.apk.
Current script validates the build/import SHA and runCount=1.

## D. Workflow status

| WS | Status | API31 | API36 | Key result |
|---|---|---|---|---|
| WS-0 | DONE | n/a | n/a | Prior changes committed, attribution corrected |
| WS-1 | DONE | Baselines PASS | Baselines PASS | Writable APK denied on API36 |
| WS-2 | DONE | C1 matrix PASS | C1 matrix PASS | Binding, routing and storage work for tested subset |
| WS-3 | DONE | PASS | PASS | Ordinary/theme/landscape inflation |
| WS-4 | DONE | Eight steps PASS | Eight steps PASS | Persistence and caught onCreate exception |
| WS-5 | DONE within task-15 scope | M-A/M-B observed | M-A/M-B observed | Java statics separated by loader, file roots separated |
| WS-6 | DONE | Reference | Regressed | No semantic alignment changes |
| WS-7 | DONE | Documented | Documented | Three PROPOSED ADRs, no production promotion |

Broader B1 plan cache/callback exit criteria remain unconfirmed; DONE above
means the explicit task-15 measurements, not arbitrary multi-instance support.

## E. V-1

Non-read-only load: API31 success; API36 SecurityException at ClassLoader construction.
Read-only load: success on both.
Status=CONFIRMED; Blocker for API34+=YES (directly reproduced on API36 only).
See [V-1](V-1-READONLY-DCL-CHECK.md). GuestStore fix is deferred to the next task.

## F. API36 regression

| Experiment | API31 | API36 |
|---|---|---|
| EXP-001 read-only copy | CONFIRMED | CONFIRMED |
| EXP-002 Option B + landscape | CONFIRMED | CONFIRMED |
| EXP-003A C0 | Baseline reproduced | Baseline reproduced |
| EXP-003B0-P | Wrapper/factory limits | Same limits |
| C1 + restart reads | PASS | PASS |
| Layout ordinary/themed/landscape | PASS | PASS |
| Minimal onCreate + restart + error | PASS | PASS |
| M-A/M-B | Observed | Same measured pattern |

Differences: writable DCL denied only on API36; default ordinary text colors
differ; onCreate requests window on API31 but not API36.
[Full comparison](API36-REGRESSION-RESULT.md).

## G. C1 matrix

| Observation | C0 | C1, both APIs | Classification |
|---|---|---|---|
| Package/loader/resources/assets/info | Guest getters | All four context forms PASS | BASE_CONTEXT_DOMINANT |
| Bound applicationContext | Wrapper | Guest Application | C0_DELEGATION_GAP repaired |
| Inflater/theme | Host-backed | C1 context, Guest theme | C0_DELEGATION_GAP repaired |
| Configuration/DP/attribution | Host derivation | Guest routing with shared binding | C0_DELEGATION_GAP repaired |
| Internal storage | Partial wrapper coverage | All requested paths PASS | BASE_CONTEXT_DOMINANT |
| Preferences/database | Host | Instance files, persistence PASS | C0_DELEGATION_GAP repaired |
| External directories | Host | Host root/sandbox/instance | BASE_CONTEXT_DOMINANT |
| Attribution/permission/services | Host | Host except inflater | HOST_IDENTITY_REALITY |
| Package query/package Context | Not installed | NameNotFoundException | UNSUPPORTED |
| Component starts/binds | Not called | Not called | UNSUPPORTED |
| Display/window contexts | Not implemented | Not implemented | DEFERRED |

[Complete matrix and limitations](EXP-003C1-CONTROLLED-CONTEXT-RESULT.md).

## H. Preferences and database

C0 marker: /data/user/0/com.example.appsandbox/shared_prefs/exp003c1.xml.
C1 CE root: /data/user/0/com.example.appsandbox/files/task15-instances/c1.
DP root: /data/user_de/0/com.example.appsandbox/files/task15-instances/c1.
Preferences use shared_prefs/<name>.xml, databases use databases/<name>.
Host corresponding files absent; Host inflater/theme unchanged on both devices.

Known differences: custom typed XML; synchronous apply; committing-thread,
strongly held listeners; clear reports individual keys; no multiprocess
coordination; deleting while retaining older preference objects unsupported.
Only tested storage interfaces are routed; this is not an OS sandbox.

## I. Layout inflation

Ordinary full marker PASS; themed text color ff12ab34 PASS; landscape marker
EXP002_LANDSCAPE PASS on both. Root=LinearLayout, child=TextView, C1 contexts.
Host inflater negative throws InflateException on colliding Host selector ID.
[Layout evidence](EXP-003C1-LAYOUT-INFLATE-RESULT.md).

## J. onCreate-min

Gate C1 + ordinary layout PASS, tied to API and Guest SHA.
Eight steps: resource PASS; Application cast PASS; file read/write PASS;
preferences PASS; SQLiteOpenHelper PASS; layout PASS; clipboard object-only
PASS; lifecycle registration PASS. No lifecycle dispatch claim.
Restart reads GUEST_ONCREATE_PREF/GUEST_ONCREATE_DB without onCreate.
Injected RuntimeException: EXP003C onCreate failure; Host survives.
Host file/preferences/database markers absent, Guest marker files present.

Initial API31 readAllBytes NoSuchMethodError preserved in raw evidence; fixed
Guest test to use basic stream reads, rebuilt and reran gates before success.
[Detailed result](EXP-003C-MIN-ONCREATE-RESULT.md).

## K. Multi-instance

### M-A

| Item | API31 | API36 |
|---|---|---|
| Counts after two Applications | 2 / 2 | 2 / 2 |
| App/Resources | Distinct | Distinct |
| Assets | Shared | Shared |
| applicationContext | Own Application | Own Application |
| Instance-2 sees instance-1 preferences/database marker | false / false | false / false |
| onCreateCalled | false | false |

### M-B

| Item | API31 | API36 |
|---|---|---|
| Counts after two Applications | 1 / 1 | 1 / 1 |
| App/Resources/loaders | Distinct | Distinct |
| Assets | Shared | Shared |
| applicationContext | Own Application | Own Application |
| Instance-2 sees instance-1 preferences/database marker | false / false | false / false |
| onCreateCalled | false | false |

Independent Java loaders isolate the tested static fields, not UID, native,
framework services or crash impact. No process model decision.
[Full paths and result](EXP-003B1-SAME-PACKAGE-MULTI-INSTANCE-RESULT.md).

## L. Service requests

WS-2: layout_inflater on both APIs.
WS-4: API31 layout_inflater/window/clipboard; API36 layout_inflater/clipboard.
Only inflater is cloned into C1. Clipboard contents were never accessed.

## M. NEEDS_NON_PUBLIC

None established by this experiment. Hidden observation NOT NEEDED NOW.
Unsupported package/factory/component/lifecycle behavior is not promoted into
a claim that non-public APIs are required. Further design and public-API
experiments are needed before considering any exception.

## N. ADRs

All Status=PROPOSED:
- ADR-0009-CONTROLLED-GUEST-CONTEXT-C1.md: bounded debug C1 contract.
- ADR-0010-GUEST-APPLICATION-ONCREATE-MIN.md: gated eight-step onCreate script.
- ADR-0011-READONLY-GUEST-CODE-API34.md: propose immutable read-only code publication.

No ADR-0006/0007/0008 status change. Production GuestStore and C0 source unchanged.

## O. Modified files

Complete cumulative list relative to checkpoint 9e96c99, including WS-0
inherited task-14 work and untouched external research submitted as requested:

- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/java/com/example/appsandbox/experiments/exp002/Exp002Runner.kt`
- `app/src/debug/java/com/example/appsandbox/experiments/exp003b0/Exp003b0Runner.kt`
- `app/src/debug/java/com/example/appsandbox/experiments/exp003b1/MultiInstanceExperiment.kt`
- `app/src/debug/java/com/example/appsandbox/experiments/exp003c/OnCreateExperiment.kt`
- `app/src/debug/java/com/example/appsandbox/experiments/exp003c1/C1Experiment.kt`
- `app/src/debug/java/com/example/appsandbox/experiments/exp003c1/Exp003c1ControlledContext.kt`
- `app/src/debug/java/com/example/appsandbox/experiments/exp003c1/ExperimentPreferences.kt`
- `app/src/debug/java/com/example/appsandbox/experiments/exp003c1/GateEvidence.kt`
- `app/src/debug/java/com/example/appsandbox/experiments/exp003c1/LayoutExperiment.kt`
- `app/src/debug/java/com/example/appsandbox/experiments/v1/ExperimentActivity.kt`
- `app/src/main/java/com/example/appsandbox/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `docs/ChatGPT/README.md`
- `docs/ChatGPT/task-14.txt`
- `docs/ChatGPT/task-15.txt`
- `docs/ChatGPT/web-search-01.txt`
- `docs/ROADMAP.md`
- `docs/adr/ADR-0009-CONTROLLED-GUEST-CONTEXT-C1.md`
- `docs/adr/ADR-0010-GUEST-APPLICATION-ONCREATE-MIN.md`
- `docs/adr/ADR-0011-READONLY-GUEST-CODE-API34.md`
- `docs/design/24_IMPLEMENTATION_ROADMAP.md`
- `docs/experiments/API36-REGRESSION-RESULT.md`
- `docs/experiments/EXP-002-RESULT.md`
- `docs/experiments/EXP-003-APPLICATION-COMPATIBILITY-LEVELS.md`
- `docs/experiments/EXP-003-HOST-LEAKAGE-MATRIX.md`
- `docs/experiments/EXP-003B-LOADEDAPK-PRECHECK.md`
- `docs/experiments/EXP-003B0-P-RESULT.md`
- `docs/experiments/EXP-003B1-SAME-PACKAGE-MULTI-INSTANCE-PLAN.md`
- `docs/experiments/EXP-003B1-SAME-PACKAGE-MULTI-INSTANCE-RESULT.md`
- `docs/experiments/EXP-003C-MIN-ONCREATE-RESULT.md`
- `docs/experiments/EXP-003C1-CONTROLLED-CONTEXT-RESULT.md`
- `docs/experiments/EXP-003C1-LAYOUT-INFLATE-RESULT.md`
- `docs/experiments/EXP-003C1-SYSTEM-SERVICE-REQUESTS.md`
- `docs/experiments/TASK-15-RESULT.md`
- `docs/experiments/V-1-READONLY-DCL-CHECK.md`
- `docs/experiments/evidence/task15/7b670025/c1-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/c1-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/c1-read-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/c1-read-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/c1-read.txt`
- `docs/experiments/evidence/task15/7b670025/c1.txt`
- `docs/experiments/evidence/task15/7b670025/device.txt`
- `docs/experiments/evidence/task15/7b670025/exp001-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/exp001-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/exp001.txt`
- `docs/experiments/evidence/task15/7b670025/exp002-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/exp002-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/exp002.txt`
- `docs/experiments/evidence/task15/7b670025/exp003a-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/exp003a-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/exp003a.txt`
- `docs/experiments/evidence/task15/7b670025/exp003b0-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/exp003b0-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/exp003b0.txt`
- `docs/experiments/evidence/task15/7b670025/layout-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/layout-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/layout.txt`
- `docs/experiments/evidence/task15/7b670025/multi-a-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/multi-a-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/multi-a.txt`
- `docs/experiments/evidence/task15/7b670025/multi-b-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/multi-b-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/multi-b.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate-error-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate-error-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate-error.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate-initial-readAllBytes-failure.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate-read-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate-read-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate-read.txt`
- `docs/experiments/evidence/task15/7b670025/oncreate.txt`
- `docs/experiments/evidence/task15/7b670025/v1-logcat.txt`
- `docs/experiments/evidence/task15/7b670025/v1-pm-path.txt`
- `docs/experiments/evidence/task15/7b670025/v1.txt`
- `docs/experiments/evidence/task15/emulator-5554/c1-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/c1-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/c1-read-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/c1-read-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/c1-read.txt`
- `docs/experiments/evidence/task15/emulator-5554/c1.txt`
- `docs/experiments/evidence/task15/emulator-5554/device.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp001-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp001-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp001.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp002-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp002-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp002.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp003a-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp003a-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp003a.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp003b0-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp003b0-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/exp003b0.txt`
- `docs/experiments/evidence/task15/emulator-5554/layout-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/layout-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/layout.txt`
- `docs/experiments/evidence/task15/emulator-5554/multi-a-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/multi-a-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/multi-a.txt`
- `docs/experiments/evidence/task15/emulator-5554/multi-b-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/multi-b-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/multi-b.txt`
- `docs/experiments/evidence/task15/emulator-5554/oncreate-error-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/oncreate-error-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/oncreate-error.txt`
- `docs/experiments/evidence/task15/emulator-5554/oncreate-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/oncreate-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/oncreate-read-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/oncreate-read-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/oncreate-read.txt`
- `docs/experiments/evidence/task15/emulator-5554/oncreate.txt`
- `docs/experiments/evidence/task15/emulator-5554/v1-logcat.txt`
- `docs/experiments/evidence/task15/emulator-5554/v1-pm-path.txt`
- `docs/experiments/evidence/task15/emulator-5554/v1.txt`
- `docs/research/virtualization/00_SOURCES.md`
- `docs/research/virtualization/07_APPLICATION_LOADEDAPK.md`
- `docs/research/virtualization/08_RESOURCES_STRATEGIES.md`
- `docs/research/virtualization/13_PROCESS_MODELS.md`
- `docs/research/virtualization/16_ANDROID_VERSION_CHANGES.md`
- `docs/research/virtualization/19_WORK_PROFILE_ALTERNATIVE.md`
- `docs/research/virtualization/23_SPLIT_APK_SUPPORT.md`
- `docs/research/virtualization/24_SIGNING_AND_PACKAGE_IDENTITY.md`
- `docs/research/virtualization/26_GAPS_IN_CURRENT_APPSANDBOX.md`
- `docs/research/virtualization/README.md`
- `scripts/task15-emulator.ps1`
- `scripts/task15-run.ps1`
- `test-guests/GuestTestApp/src/main/AndroidManifest.xml`
- `test-guests/GuestTestApp/src/main/java/com/example/appsandbox/testguest/runtime/Exp003GuestApplication.java`
- `test-guests/GuestTestApp/src/main/java/com/example/appsandbox/testguest/runtime/Exp003GuestComponentFactory.java`
- `test-guests/GuestTestApp/src/main/java/com/example/appsandbox/testguest/runtime/Exp003OnCreateThrowingApplication.java`
- `test-guests/GuestTestApp/src/main/java/com/example/appsandbox/testguest/runtime/Exp003ThrowingApplication.java`
- `test-guests/GuestTestApp/src/main/res/layout/exp003_themed_layout.xml`
- `test-guests/GuestTestApp/src/main/res/values/attrs.xml`
- `test-guests/GuestTestApp/src/main/res/values/themes.xml`

## P. Git and verification

No push. WS-7 is a dedicated docs commit. To inspect the final state:
```powershell
git status --short
git log --oneline -15
```

History immediately before WS-7 (its commit is necessarily not self-addressable):
```text
53bc38f test(WS-6): record API36 C1 layout onCreate and multi-instance regression
46c7650 test(WS-1): confirm API36 read-only code requirement and complete baseline regression
f915fb1 test(WS-5): observe shared and independent loader multi-instance behavior on API31
d6af4da test(WS-4): gate minimal Guest onCreate and verify persistence and exception containment on API31
0b1362c test(WS-3): verify Guest layouts theme and landscape through C1 on API31
c3591d0 feat(WS-2): implement public C1 context and verify API31 storage and binding matrix
9ee2ac0 test(WS-1): add fresh-process public regression harness and record API31 baseline; API36 pending
18922f0 docs(WS-0): correct B0 delegation attribution and application binding semantics
cb0ceeb docs(task-15): preserve task specifications and external research
f44378c feat(task-14): record EXP-003B0-P, B1 plan and V-1 registration
9e96c99 chore(checkpoint): record EXP-002 and EXP-003A/B0 design work
30db956 Implement AppSandbox experiments and runtime design
38354cc first commit
```

Debug Host and clean Guest assembleDebug succeeded. Host assembleRelease
succeeded, including lintVitalRelease. git diff --check passed.
All final device reports have runCount=1. Release build excludes the debug
manifest/experiment sources. Experiment outputs, actual exceptions and SHA
verification are retained under evidence/task15/<serial>/.

## Q. Next step

Ready for component stage design (Activity hosting design only): YES.
Reasons: bounded C1/creation/onCreate evidence exists on both APIs; unresolved
factory, lifecycle, identity and production code-publication limits are explicit.
This does not authorize starting or implementing any Guest components.

Hidden observation request needed: NO.
Reason: measured behaviors are explained by public context routing and Host
identity; no remaining experiment question demands hidden field certainty.
