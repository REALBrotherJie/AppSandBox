# EXP-003B1 same-package multi-instance

API31 Mi 10: observed, LIMITED. No process model decision.
Evidence: evidence/task15/7b670025/multi-a.txt and multi-b.txt.
PID 13597 / 14472, runCount=1 each. onCreateCalled=false in both instances.

## M-A: shared DexClassLoader

| Observation | Instance 1 | Instance 2 |
|---|---|---|
| Constructor static count after both creations | 2 (initially 1) | 2 |
| Application identity | Distinct | Distinct |
| Class / loader | Shared | Shared |
| Resources | Distinct | Distinct |
| Assets | Shared | Shared |
| applicationContext | Own Application | Own Application |
| Data / prefs / database | M-A-instance-1 | M-A-instance-2 |
| Marker written to instance 1 | Prefs and DB readback true | Prefs and DB visibility false |

## M-B: independent DexClassLoaders

| Observation | Instance 1 | Instance 2 |
|---|---|---|
| Constructor static count | 1 | 1 |
| Application identity | Distinct | Distinct |
| Class / loader | Distinct | Distinct |
| Resources | Distinct | Distinct |
| Assets | Shared | Shared |
| applicationContext | Own Application | Own Application |
| Data / prefs / database | M-B-instance-1 | M-B-instance-2 |
| Marker written to instance 1 | Prefs and DB readback true | Prefs and DB visibility false |

All paths are beneath Host files/task15-instances; exact absolute paths and
identity hashes are in raw evidence. Host prefs/database negatives pass.
API36: same observations, see comparison below.

Implication for research/virtualization/13_PROCESS_MODELS.md: separate loaders
isolate the tested Java statics but do not provide separate UID, process,
framework services, crash isolation or native globals. Resources wrappers can
be distinct while Assets remain shared. C1 file routing is logical isolation.
Callbacks, framework caches and mutation of shared resources are untested;
the broader plan's exit criteria are therefore only partially covered.

## API36 regression (WS-6)

| Observation | API31 M-A / M-B | API36 M-A / M-B |
|---|---|---|
| Constructor counts | 2,2 / 1,1 | 2,2 / 1,1 |
| Application / Resources identities | Distinct / distinct | Distinct / distinct |
| Assets identity | Shared / shared | Shared / shared |
| Application context binding | Own / own | Own / own |
| Instance 2 prefs/database sees instance 1 marker | false / false | false / false |
| onCreate called | false / false | false / false |

Evidence: evidence/task15/emulator-5554/multi-a.txt (PID 6941), multi-b.txt
(PID 7008), runCount=1 each. Exact file paths/identity hashes are retained.
