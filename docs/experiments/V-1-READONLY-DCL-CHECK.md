# V-1 read-only DexClassLoader check

Status: CONFIRMED on Android 16/API36 emulator, Host targetSdk=36.
Blocker for API34+ = YES. GuestStore production code remains unchanged.

Task-14 BLOCKED_NO_DEVICE was incorrect: an existing AVD and API36 images
were present. Task-15 corrected to PENDING and executed the comparison.

| Observation | Mi 10/API31 | Emulator/API36 |
|---|---|---|
| Writable imported base.apk | GuestProbe.ping succeeds | SecurityException during DexClassLoader construction |
| Read-only copied APK | GuestProbe.ping succeeds | GuestProbe.ping succeeds |
| Guest installed | No, before and after | No, before and after |

API36 PID=5163, runCount=1. Actual exception:
`java.lang.SecurityException: Writable dex file '<private GuestStore revision>/base.apk' is not allowed.`
Full path/message: [raw evidence](evidence/task15/emulator-5554/v1.txt).
The copy was set read-only and its hash matched the import. Final build and
both device imports: 666fbb1ec28d5c36e8dcde928e67bbd95fc31d9025beeaff1af3aace2b536494.

Recommendation for next task: finalize code-file permissions in GuestStore's
import completion path before publishing a registry record/loading code, with
permission failure handling and immutable revision semantics from ADR-0003.
No production permission fix is made here. API34/35 are not directly tested;
the API34+ blocker is the support-range risk reproduced on API36.
