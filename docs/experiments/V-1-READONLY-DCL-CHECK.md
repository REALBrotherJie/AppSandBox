# V-1 Read-only DexClassLoader Check

## Status

`PENDING` (task-15 audit correction)

The task-14 claim that no emulator existed was incorrect. The existing API36
AVD was found. Startup required a process-local ANDROID_SDK_ROOT correction;
the initial startup remains ADB offline. API36 testing is pending, not absent.
API31 fresh-process results are in evidence/task15/7b670025/v1.txt: both
writable and read-only copies loaded GuestProbe and invoked ping successfully.

```text
Device API level = 31
Non-read-only load result = NOT RUN (not an API34+ device)
Read-only load result = NOT RUN (not an API34+ device)
Status = PENDING_API36
```

The risk remains on the roadmap: after GuestStore writes `base.apk`, a future
API34+ test must compare a non-read-only copy with a read-only copy using
DexClassLoader and record the real platform result.
