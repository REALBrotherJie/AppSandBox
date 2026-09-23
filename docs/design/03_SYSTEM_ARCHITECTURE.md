# AppSandbox System Architecture

## Design premise

The system is derived from the missing-installation gaps, not from any existing virtualization product. It is a host application that owns guest metadata and execution policy.

```text
                 Android platform
        PMS / ATMS / AMS / system services
                    ^       ^
                    | real host identity
              AppSandbox Host
                    |
        Sandbox Coordinator and policy boundary
       /       |          |          \
  Registry  Storage   Code/Resources  Bridges
       \       |          |          /
             Guest Runtime
        /      |       |       \
   Application Activity Service Provider
                    |
              Guest APK archive
```

## Modules

| Module | Owns | Does not own |
|---|---|---|
| Host UI | user consent/import/display | guest lifecycle |
| Package Registry | immutable package metadata and revisions | class loading |
| Storage Space | instance paths, quotas, atomic file operations | Android UID isolation |
| Code Runtime | guest dex/native loading policy | Activity/task scheduling |
| Resource Runtime | guest asset/resource view | host resources |
| Application Runtime | guest application context/lifecycle | system install |
| Component Runtime | logical component instances and state | kernel process identity |
| System Bridge | selected API translation and policy | arbitrary Binder hook |
| Identity Model | mapping of logical and Android identities | fake UID |
| Process Runtime | future process placement | SELinux re-labeling |
| Permission Broker | guest capability decisions | granting host permissions silently |
| Multi-instance | package/instance relationship | duplicate APK copies by default |

## Process placement

`PROPOSED`: begin in one host process only for metadata and loading experiments. A later `Sandbox Process` should be introduced before running untrusted guest code or long-lived components. A separate process improves crash containment but still shares the host app UID/domain.

## State

Persistent: package revisions, verified APK digest, component metadata, instance paths, guest permission decisions, user-visible enablement, migration version.

In-memory: loaded class/resource objects, component instances, pending transactions, Binder handles, resolver caches.

System services see: host package, host UID, host process, host permissions, and any explicit host component used at the boundary. Guest code should see: guest logical package/context/data paths and only the subset of services deliberately adapted.

## Communication

Use direct Kotlin calls inside the first process. Introduce a narrow internal protocol only when crossing a process boundary. Do not create a general event bus or a Binder proxy layer before a concrete boundary requires it.

## Invariants

1. Package identity and instance identity are different.
2. Every guest path is derived from validated IDs, never raw package input.
3. No guest action bypasses the policy boundary.
4. Unsupported system behavior fails explicitly.
5. Runtime code is not added until its prerequisite experiment and ADR exist.
