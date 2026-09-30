# Non-goals

AppSandbox does not aim to be:

- a complete Android OS virtual machine;
- a kernel namespace or unique-UID sandbox;
- a root, Magisk, or Xposed product;
- a game cheat or anti-detection system;
- a DRM, payment, banking, authentication, or account-control bypass;
- an automation system for other apps;
- a third-party network protocol cracking tool;
- a device identity impersonation layer;
- a modifier of third-party server requests.

These non-goals are architectural constraints, not temporary omissions.

Direction A does use a centralized hidden-API access layer, system-service Binder proxies, manifest stub components, ActivityThread/Instrumentation integration, and arm64 native IO redirection to run installed applications in isolated clone instances. Those mechanisms are product architecture, not security-control bypass features.
