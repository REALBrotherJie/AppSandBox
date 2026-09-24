# ADR-0009: Controlled Guest Context C1

## Status

PROPOSED

## Context and proposed decision

Task-15 found that B0 inflater/configuration behavior was a C0 delegation gap,
not evidence requiring hidden LoadedApk access. Keep C0 as the baseline and
evaluate a public-only C1 facade with Host applicationContext as its base.
Bind Guest Application after newApplication; share binding with derived C1s.
Clone/cache inflater per C1, create themes from Guest Resources, and route
tested storage operations under the instance root.

## Confirmed

Mi 10 Android 12/API31 arm64 and Android 16/API36 x86_64 emulator: base,
configuration, device-protected and attribution matrices pass; application
context resolves to Guest Application; Guest layouts and custom theme color
inflate correctly; preferences and SQLite persist across process restarts;
Host negative controls pass. See EXP-003C1-CONTROLLED-CONTEXT-RESULT.md and
EXP-003C1-LAYOUT-INFLATE-RESULT.md in ../experiments/ for evidence.

## Not confirmed and consequences

This is debug-only, not production isolation. Custom XML preferences use
synchronous apply, caller-thread listeners and no multiprocess coordination.
UID, permission, attribution and non-inflater services remain Host-backed.
PackageManager self-query, display/window Contexts, lifecycle dispatch and
Guest component hosting are not supported/confirmed. API28-30/32-35 and
third-party application compatibility are untested. No hidden API is proposed.
Reviewer approval and a production contract are required before promotion.
