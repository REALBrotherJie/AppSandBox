# ADR-0010: Minimal Guest Application onCreate

## Status

PROPOSED

## Proposed decision

Permit a narrowly gated debug script: public Instrumentation.newApplication,
C1.bindApplication, then one callApplicationOnCreate in a fresh process.
Require C1 matrix/negative controls and ordinary layout PASS for the same
API level and Guest APK SHA. Gate failures skip the script.

## Confirmed

Mi 10/API31 and Android 16/API36 x86_64 emulator: all eight scripted steps pass
(resource, Application cast, file, preferences, database, layout, clipboard
object acquisition, callback registration). Preferences/database persist in
a new process without calling onCreate. Deliberate onCreate RuntimeException
is caught and Host survives. Host marker files remain absent.
Evidence: ../experiments/EXP-003C-MIN-ONCREATE-RESULT.md.

## Not confirmed and consequences

Not a full Android Application runtime. No Guest components, providers or
framework-dispatched lifecycle callbacks were started/tested. Custom Guest
AppComponentFactory is not used by app-created Instrumentation. Clipboard
contents were not accessed. Arbitrary SDK initialization, cleanup, ANR/crash
containment, native code and other Android releases are unconfirmed.
Same-process exception catching is not a security or crash isolation boundary.
