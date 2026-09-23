# AppSandbox Master Design

Status: Design baseline, not a runtime implementation.

## Purpose

AppSandbox explores running a user-selected APK inside an installed host while being explicit about the difference between logical compatibility and Android OS-level installation/security. The current project intentionally stops at APK import and metadata parsing.

## Core problem

An installed app is backed by PMS state, UID/SELinux identity, user data paths, component registration, process creation, class/resources setup, permissions, and system-service attribution. An uninstalled APK has archive bytes but none of those system-owned results. The gap analysis is the source of the architecture.

## Architecture

The core modules are Host, Package Registry, Storage Space, Identity, Permission Broker, Code Runtime, Resource Runtime, Application Runtime, Component Runtime, System Bridge, Process Runtime, Native Runtime, and Multi-instance Coordinator. They are deliberately narrow and evidence-driven.

The host owns persistent package/instance state. Future guest execution may move to a sandbox process, but it will still not have a unique Android UID or SELinux domain unless the platform participates.

## Documents

- [Android runtime model](design/01_ANDROID_APP_RUNTIME_MODEL.md)
- [Gap analysis](design/02_GUEST_RUNTIME_GAP_ANALYSIS.md)
- [System architecture](design/03_SYSTEM_ARCHITECTURE.md)
- [Package runtime](design/04_PACKAGE_RUNTIME.md)
- [Storage model](design/05_STORAGE_MODEL.md)
- [Code loading](design/06_CODE_LOADING.md)
- [Resource loading](design/07_RESOURCE_LOADING.md)
- [Application runtime](design/08_APPLICATION_RUNTIME.md)
- [Activity runtime](design/09_ACTIVITY_RUNTIME.md)
- [Service runtime](design/10_SERVICE_RUNTIME.md)
- [Broadcast runtime](design/11_BROADCAST_RUNTIME.md)
- [Provider runtime](design/12_PROVIDER_RUNTIME.md)
- [System services](design/13_SYSTEM_SERVICE_COMPATIBILITY.md)
- [Identity](design/14_IDENTITY_MODEL.md)
- [Process model](design/15_PROCESS_MODEL.md)
- [Native runtime](design/16_NATIVE_RUNTIME.md)
- [Permissions](design/17_PERMISSION_MODEL.md)
- [Multi-instance](design/18_MULTI_INSTANCE.md)
- [Version matrix](design/19_ANDROID_VERSION_MATRIX.md)
- [Hidden API strategy](design/20_HIDDEN_API_STRATEGY.md)
- [Security](design/21_SECURITY_MODEL.md)
- [Product constraints](design/22_PRODUCT_AND_PLAY_CONSTRAINTS.md)
- [Module graph](design/23_MODULE_DEPENDENCY_GRAPH.md)
- [Implementation roadmap](design/24_IMPLEMENTATION_ROADMAP.md)
- [Experiment plan](design/25_EXPERIMENT_PLAN.md)
- [Glossary](design/26_GLOSSARY.md)
- [Non-goals](design/27_NON_GOALS.md)

ADRs are in [docs/adr](adr/ADR_TEMPLATE.md). Clean-room rules remain in [CLEAN_ROOM.md](CLEAN_ROOM.md).

## Status policy

Every important statement must be marked `CONFIRMED`, `PROPOSED`, `RESEARCH NEEDED`, or `BLOCKED`. Android 16 behavior is not asserted without a device/AOSP verification. No runtime feature is authorized by this document alone.

## Development rule

```text
Research -> Design -> Experiment -> ADR -> Implementation -> Verification
```

## Immediate recommendation

The next milestone should validate the package/storage foundation and registry invariants without launching a guest. If a runtime assumption remains uncertain, create a minimal isolated experiment first rather than changing production code.
