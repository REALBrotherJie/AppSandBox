# GuestTestApp Specification

This is a future independent Android test project. This milestone creates only the specification.

| Phase | Capability | Toggle |
|---|---|---|
| A | ordinary Java/Kotlin class with pure method | `feature.class` |
| B | string, drawable, layout, theme | `feature.resources` |
| C | custom Application | `feature.application` |
| D | Activity UI | `feature.activity` |
| E | preferences, files, SQLite | `feature.storage` |
| F | Service start/bind/stop | `feature.service` |
| G | static/dynamic receiver | `feature.receiver` |
| H | ContentProvider/authority | `feature.provider` |
| I | native library/JNI | `feature.native` |

Requirements: deterministic output, no network dependency, no third-party virtualization code, one feature per build flavor or manifest switch, versioned expected results, and a small diagnostic screen/log protocol.

The app must be built and signed by the project team. Real third-party APKs are not first-line test inputs.
