# EXP-003 Android Application Model

## Scope

This document describes the normal Android client-side Application creation
model. It is a design reference only. AppSandbox does not copy
`ActivityThread`, `LoadedApk`, `ContextImpl`, or `Instrumentation` internals
and does not call hidden APIs in this design round.

## Normal Chain

```text
system_server/PMS package records
        |
        v
ActivityThread.bindApplication
        |
        v
LoadedApk and package class loader/resources
        |
        v
ContextImpl
        |
        v
Instrumentation.newApplication
        |
        v
Application.attachBaseContext
        |
        v
Application.onCreate
```

`ActivityThread`, `LoadedApk`, and `ContextImpl` coordinate framework-created
state. `Instrumentation.newApplication(ClassLoader, String, Context)` is the
public application-instantiation entry point. It loads the named class with
the supplied loader, creates the Application object, and attaches the supplied
Context. It does not establish PMS installation, a new UID, a process, a
PackageManager virtualization layer, or a complete Guest runtime. `onCreate`
is a separate lifecycle action and must be tested separately.

## Capability Ownership

| Capability | Client process | system_server/PMS | Both |
|---|---:|---:|---:|
| Application Java object | yes | no | |
| ClassLoader | yes | | |
| Resources | yes | metadata/source contributes | yes |
| packageName | | source metadata | yes |
| dataDir | client view | install/storage policy | yes |
| UID | | yes | |
| ApplicationInfo | local copy | authoritative package record | yes |
| PackageManager queries | client proxy | authoritative service | yes |
| ContentProvider install/binding | | yes | yes |
| system-service identity | | yes | yes |

The host process remains the real caller. A Guest logical package name does
not change the kernel UID, SELinux domain, PID, Binder caller identity, or
system_server package authorization.

## EXP-003 Implication

The first experiment should construct a deliberately limited Context and use
the public `Instrumentation.newApplication` path. Success means only that a
Guest Application object can be attached to the controlled Context. It does
not mean Android performed a real `bindApplication` for the Guest.

