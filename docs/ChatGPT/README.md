# ChatGPT Task Index

## Current Authoritative Status

- M2-M11: `CLOSED / PASS` by Planner.
- M12: `FROZEN / PARTIAL`; initial implementation, one FIX, and Architecture Review did not cross the complete production system-service boundary.
- M13: `NOT STARTED`.
- Real App Compatibility Audit: `COMPLETED`; most real Apps first block in M2-M10 scope, so resuming M12 is not currently recommended.
- Current audit: `docs/experiments/REAL-APP-COMPATIBILITY-AUDIT.md` and `docs/Claude/Task/REAL-APP-COMPATIBILITY-AUDIT-RESULT.md`.

| Task | Purpose | Status |
|---|---|---|
| task-09 | External Android virtualization technology survey | Historical research task |
| task-10 | Duplicate of task-09 | Duplicate, retained for history |
| task-11 | EXP-002 architecture correction and LoadedApk precheck | Checkpoint 9e96c99 |
| task-12 | EXP-003A Controlled Context C0 baseline | Checkpoint 9e96c99 |
| task-13 | EXP-003B LoadedApk research, route comparison, and B0 design | Checkpoint 9e96c99, docs only |
| task-14 | EXP-003B0 public-only impact observation | Committed f44378c; attribution corrected 18922f0 |
| task-15 | WS-0 through WS-7, C1/layout/onCreate/multi-instance/API36 | Implemented and locally committed by workflow; no push; see experiments/TASK-15-RESULT.md |
| task-16 | Production GuestStore immutable artifact hardening | Implemented; see experiments/TASK-16-GUESTSTORE-HARDENING-RESULT.md |
| task-17 | Activity runtime/carrier architecture research and design only | Docs only; no Activity runtime implementation |

task-09 and task-10 have identical content. Neither is deleted.
