# Task-45 Resolver/Dispatch Integration

Date: 2026-09-27  
Status: PARTIALLY CONFIRMED

## Integration
- Base: `fe4e81b`.
- Task-43 cherry-pick: `13a5992` (original `8961220`).
- Task-44 cherry-pick: `7eb4ed1` (original `9168521`).
- Integration branch: `codex/task45-integration`.
- No push; user-maintained `docs/Codex/*` remains untracked and untouched.

## Implementation decisions
- Bounded resolver now exposes `DEFAULT_ONLY` versus `GENERAL` policy.
- Activity implicit matching requires `android.intent.category.DEFAULT` under `DEFAULT_ONLY`; Service/Receiver do not inherit that rule.
- Candidate ordering remains deterministic for diagnostics only. Exactly one eligible candidate resolves; multiple candidates return `Ambiguous` and fail closed.
- `prepareImplicit()` is the single Resolver-to-Dispatch adapter. Dispatch does not re-resolve.
- Plan identity and a restricted intent snapshot type are available without persisting framework component objects.

## Verification
- `git diff --check`: passed.
- Gradle JVM/build verification: passed after normalizing to the complete SDK; 107 JVM tests passed (1 skipped), and full `test`, `assembleDebug`, and `assembleRelease` passed.
- API31: device remained online, but the existing Task-42 runner stalled in its ADB/uiautomator capture path; no end-to-end pass is claimed.
- API36: AVD cold-booted and reached SDK 36 `sys.boot_completed=1`; the Task-43 runner likewise stalled during capture, so parity and device matrix are not claimed.
- Guest installed: false / false.

## Remaining gate
Repair the existing ADB/uiautomator runners, then run the full Task-42 regression and the Task-45 resolver, framework parity, stale-plan, migration, and logical-dispatch matrix before updating `main`.
