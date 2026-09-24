# EXP-003 Application Compatibility Levels

| Level | Meaning | Gate |
|---|---|---|
| A0 | Application object instantiated | 003B |
| A1 | Basic Guest Context getters correct | 003A |
| A2 | Per-instance file and cache storage works | 003A |
| A3 | SharedPreferences works in instance scope | C1 debug API31/API36, custom XML and synchronous apply limitations |
| A4 | Self-only PackageManager queries work | future |
| A5 | Selected system service works with identity audit | future |
| A6 | ContentResolver/provider boundary works | future |
| A7 | Third-party SDK initialization | future, separate review |

EXP-003A targets A1 and A2. EXP-003B targets A0 plus attach observations.
EXP-003C targets only a minimal Guest `onCreate` using A1/A2 capabilities.
No level implies real Android package identity.

Task-15: C1 experimentally reaches A0-A3 on Mi 10/API31 and emulator/API36.
Minimal Guest onCreate passes eight scripted steps. A4 remains unsupported
(Guest self PackageManager query fails); A5 only has object acquisition for
clipboard, not complete service operations. A6/A7 are unconfirmed. These levels
are coverage labels, not a claim that all higher numbered behavior is supported.
Lifecycle dispatch, third-party SDK initialization and components remain untested.
