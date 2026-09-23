# Glossary

**Host**: the installed AppSandbox Android application.

**Guest APK**: user-selected APK bytes and metadata managed by AppSandbox.

**Guest Package**: logical package/revision description, independent of an installed PMS package.

**Guest Instance**: one isolated logical data/runtime state for a package.

**Guest Runtime**: code, resource, application, and component execution layers.

**Sandbox Process**: future host-owned process for guest execution; not an OS-level UID sandbox.

**Logical Identity**: AppSandbox package/instance/process identity.

**Android Identity**: UID, PID, userId, package attribution, SELinux domain, and system-server view.

**Component**: Activity, Service, BroadcastReceiver, or ContentProvider.

**Registry**: persistent AppSandbox-owned package metadata.

**Bridge**: a typed adapter between guest logical APIs and host/public Android APIs.
