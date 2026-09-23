# EXP-003C-min onCreate

API31 Mi 10: PASS for this minimal script only. API36 pending.
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
