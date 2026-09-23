# Compatibility Tiers

## Tier 1: pure Java/Kotlin

No native code, no privileged APIs, simple storage/resources, no complex components or anti-tamper. First target after Level 1-5 gates.

## Tier 2: ordinary AndroidX

AndroidX, SQLite, network, basic services/receivers/providers. Requires service/resource/context compatibility experiments.

## Tier 3: native application

JNI and `.so` loading, ABI selection, native globals, linker behavior. Requires a separate process and native test evidence.

## Tier 4: large complex application

Many providers/services, WebView, account/push/analytics SDKs, background behavior, extensive reflection. Support must be app-specific, not assumed from Tier 1/2.

## Out of scope

Banking/high-security apps, DRM, anti-cheat, hardware attestation, Play Integrity-dependent behavior, and apps whose requirements conflict with the project's security boundary.

Each tier needs a GuestTestApp phase, pass/fail criteria, API-level matrix, and explicit unsupported list.
