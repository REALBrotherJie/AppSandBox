# EXP-003C-min onCreate

API31 Mi 10 and API36 emulator: PASS for this minimal script only.
Gate: C1 full matrix/Host negatives and ordinary layout PASS, checked against
device API and Guest APK SHA before callApplicationOnCreate. Final APK:
666fbb1ec28d5c36e8dcde928e67bbd95fc31d9025beeaff1af3aace2b536494
(30356 bytes). Imported SHA matches; pm path empty before/after.

Evidence: evidence/task15/7b670025/oncreate.txt (PID 28736),
oncreate-read.txt (PID 29669), oncreate-error.txt (PID 30542).
Each runCount=1; separate fresh processes; normal script called once.

| Guest step | API31 |
|---|---|
| Resource marker | PASS |
| Cast applicationContext to Guest Application | PASS |
| File write/read | PASS |
| SharedPreferences commit/read | PASS |
| SQLiteOpenHelper write/read | PASS |
| Ordinary layout/TextView | PASS |
| Clipboard object only | PASS, android.content.ClipboardManager; no content accessed |
| Lifecycle callback registration | PASS; dispatch not established |

Fresh-process read without onCreate returns GUEST_ONCREATE_PREF and
GUEST_ONCREATE_DB, onCreateCalled=false. Host files/preferences/database
negative checks all true (absent); all Guest marker files exist in oncreate
instance. Host survives RuntimeException: EXP003C onCreate failure.

Initial script failure is preserved in oncreate-initial-readAllBytes-failure.txt:
API31 InputStream.readAllBytes caused NoSuchMethodError. Replaced test-only
readback with basic stream reads, rebuilt/imported a new revision, reran gates.
This was a Guest test API compatibility bug, not a Context or hidden API issue.

No Guest components started. Factory compatibility, framework lifecycle dispatch,
arbitrary third-party Application behavior and OS isolation remain unconfirmed.

## API36 regression (WS-6)

| Check | API31 | API36 |
|---|---|---|
| Gate C1 + ordinary layout | PASS | PASS |
| Steps 1-8 | 8 PASS | 8 PASS |
| Preferences/database restart persistence | PASS | PASS |
| Host files/preferences/database negatives | PASS | PASS |
| Guest marker files exist | PASS | PASS |
| Throwing onCreate | RuntimeException, Host survives | Same class/message, Host survives |

Evidence: evidence/task15/emulator-5554/oncreate.txt (PID 6710),
oncreate-read.txt (6798), oncreate-error.txt (6873). Each runCount=1.
Read-only experiment has onCreateCalled=false. No implementation changes
were made to align API36 behavior.
