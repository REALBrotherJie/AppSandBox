# ADR-0001: Clean-room design source

Status: Accepted

## Context

AppSandbox must be independently designed and must not derive architecture or code from application virtualization projects.

## Decision

Use Android Developers documentation, Android SDK behavior, AOSP, Linux/Binder/ART/JNI/ELF mechanisms, and AppSandbox experiments as design evidence. Do not inspect, download, quote, or structurally copy other virtualization implementations.

## Consequences

The project may need more experiments and may reach different compatibility limits. Design reviews must cite platform mechanisms and record uncertainty.

## Risks

Public Android behavior can still vary by release/OEM. Each critical assumption needs a versioned experiment.
