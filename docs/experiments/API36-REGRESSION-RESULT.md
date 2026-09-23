# Task-15 API36 regression

WS-1 is PARTIAL: the configured AVD exists and was launched, but initially
remained offline. No API36 result is inferred from API31.

Reproduction: scripts/task15-run.ps1 -Serial emulator-5554 -Install -ImportGuest.
Each mode force-stops the Host first, records PID/runCount and pm path before
and after, and preserves original output under evidence/task15/<serial>.
All regression modes use an explicitly logged read-only experimental copy;
GuestStore and the imported immutable revision are not modified.

API31 baseline evidence: evidence/task15/7b670025/. B0 Activity comparison in
this harness uses ExperimentActivity, not MainActivity; the original task-14
MainActivity comparison remains recorded separately.
