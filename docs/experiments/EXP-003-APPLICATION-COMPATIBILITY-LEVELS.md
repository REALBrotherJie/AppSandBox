# EXP-003 Application Compatibility Levels

| Level | Meaning | Gate |
|---|---|---|
| A0 | Application object instantiated | 003B |
| A1 | Basic Guest Context getters correct | 003A |
| A2 | Per-instance file and cache storage works | 003A |
| A3 | SharedPreferences works in instance scope | future |
| A4 | Self-only PackageManager queries work | future |
| A5 | Selected system service works with identity audit | future |
| A6 | ContentResolver/provider boundary works | future |
| A7 | Third-party SDK initialization | future, separate review |

EXP-003A targets A1 and A2. EXP-003B targets A0 plus attach observations.
EXP-003C targets only a minimal Guest `onCreate` using A1/A2 capabilities.
No level implies real Android package identity.

