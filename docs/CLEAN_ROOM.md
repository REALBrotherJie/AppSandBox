# Clean-room development

AppSandbox is independently designed and implemented. No source code from VirtualApp, BlackBox, DroidPlugin, sandbox, VirtualXposed, or comparable application virtualization projects was used, copied, mechanically renamed, or used as a structural template.

Technical sources for this phase are Android SDK APIs, Android Developers documentation, AOSP behavior, and local experiments. Future work must follow: requirement -> Android mechanism analysis -> AOSP/API research -> experiment -> independent design -> implementation -> test.

This phase intentionally contains no Binder/AMS/ATMS/PackageManager hooks, hidden API bypass, native hook, runtime launch, or anti-detection behavior.

The same clean-room rule applies to the full design set under `docs/design/` and all future experiments. See [APP_SANDBOX_MASTER_DESIGN.md](APP_SANDBOX_MASTER_DESIGN.md) and [adr/ADR-0001-CLEAN-ROOM-DESIGN-SOURCE.md](adr/ADR-0001-CLEAN-ROOM-DESIGN-SOURCE.md).
