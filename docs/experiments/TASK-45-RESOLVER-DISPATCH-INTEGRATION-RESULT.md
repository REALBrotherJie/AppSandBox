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
- Gradle JVM/build verification: blocked before compilation because the configured SDK lacks accepted licenses and installed `platforms;android-36` / `build-tools;35.0.0`.
- API31: not executed in this environment.
- API36: not executed; SDK/emulator environment unavailable.
- Guest installed: false / false.

## Remaining gate
Install/restore the configured Android SDK and API31/API36 devices, then run the full Task-42 regression and the Task-45 resolver, ambiguity, stale-plan, migration, and logical-dispatch matrix before updating `main`.
