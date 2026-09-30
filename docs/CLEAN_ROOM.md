# Clean-room development

AppSandbox is independently designed and implemented. No source code from VirtualApp, BlackBox, DroidPlugin, sandbox, VirtualXposed, or comparable application virtualization projects was used, copied, mechanically renamed, or used as a structural template.

Technical sources for this phase are Android SDK APIs, Android Developers documentation, AOSP behavior, and local experiments. Future work must follow: requirement -> Android mechanism analysis -> AOSP/API research -> experiment -> independent design -> implementation -> test.

Direction A permits independently implemented hidden-API access, Binder service proxies, manifest stub components and processes, ActivityThread/Instrumentation/ClientTransaction integration, and arm64 native IO redirection. These mechanisms must be derived from Android SDK/AOSP behavior and local experiments, never copied from another virtualization project.

Anti-cheat evasion, Play Integrity or SafetyNet countermeasures, payment/banking security bypass, and device identity impersonation remain prohibited.

The same clean-room rule applies to the full design set under `docs/design/` and all future experiments. See [APP_SANDBOX_MASTER_DESIGN.md](APP_SANDBOX_MASTER_DESIGN.md) and [adr/ADR-0001-CLEAN-ROOM-DESIGN-SOURCE.md](adr/ADR-0001-CLEAN-ROOM-DESIGN-SOURCE.md).
