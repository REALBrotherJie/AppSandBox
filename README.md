# AppSandBox

Independent Android application virtualization project for running isolated clone instances of applications already installed on the device.

The current direction builds a clean-room virtualization runtime around:

- installed-package discovery through launcher intent queries, without `QUERY_ALL_PACKAGES`;
- a centralized hidden-API access layer;
- predeclared stub component processes managed by a virtual-system runtime;
- reusable instance registry, package reader, component resolver, and runtime IPC foundations.

Release builds do not execute imported APK files. Debug-only import tooling may be used for development. No source or structure is copied from VirtualApp, BlackBox, DroidPlugin, or similar projects, and the project does not implement anti-cheat, integrity, payment/banking, or device-identity bypasses.
