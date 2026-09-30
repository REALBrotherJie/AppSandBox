# ADR-0013: Direction A virtualization boundary

Date: 2026-09-30
Status: Accepted

AppSandBox targets real clone instances of applications already installed on the device. Release builds obtain executable code only from installed packages and discover candidates with MAIN/LAUNCHER `<queries>`; they do not request `QUERY_ALL_PACKAGES` or execute imported APK files.

Allowed mechanisms are centralized hidden-API access, system-service Binder proxies, manifest-declared stub components and process pools, Instrumentation/ActivityThread/ClientTransaction integration, and independently implemented arm64 native inline hooks for IO redirection.

The implementation must remain clean-room. Source code or project structure from VirtualApp, BlackBox, DroidPlugin, or similar virtualization projects must not be copied. Existing research documents may identify Android problems and root causes, but are not implementation templates.

Prohibited work includes anti-cheat evasion, Play Integrity or SafetyNet countermeasures, payment or banking security bypass, device identity impersonation, and release execution of code from non-installed APK files.
