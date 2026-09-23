# Master Design Review

Review date: 2026-09-23

## Executive finding

The Master Design correctly identifies that an uninstalled APK lacks PMS state, Android UID, SELinux identity, system component registration, and normal process lifecycle. The largest weakness is that several later designs assume a successful guest Context/Application/Activity boundary before the public API surface and process model have been experimentally proven.

## Findings

### CRITICAL-01: Activity feasibility is under-specified

**Problem:** the Activity design compares strategies but does not yet separate object construction from system-owned task/window/token semantics.

**Impact:** a successful Java Activity object could be mistaken for a real Android Activity runtime. Task, result, saved state, configuration, window, and process-death behavior may invalidate the strategy.

**Modules:** Activity, Application, Process, System Bridge.

**Recommendation:** split Activity work into client-object and system-cooperation gates. Do not implement until EXP-001 through Application and a dedicated Activity experiment succeed.

**Experiment:** required.

### CRITICAL-02: Process model is not an implementation detail

**Problem:** the design keeps host-process experiments and future sandbox process options, but does not make process placement a prerequisite for static state, JNI, crash, and identity decisions.

**Impact:** a later move from one process to many can invalidate Context, Binder, lifecycle, and class-loader contracts.

**Modules:** Process, Code, Native, Multi-instance.

**Recommendation:** use host process only for metadata and harmless loading experiments; decide the first executable-guest process boundary before Application runtime.

**Experiment:** required.

### HIGH-01: System Bridge can become a circular dependency

**Problem:** Activity/Service/Provider may need a System Bridge, while the Bridge may need component state and lifecycle.

**Impact:** modules can call one another recursively and make unsupported system behavior appear available.

**Modules:** System Bridge, Component Runtime, Identity, Permission.

**Recommendation:** System Bridge depends on immutable identity/policy and returns typed results. Component runtimes may consume it, but it must not call component runtimes to implement its base contract.

**Experiment:** partly required; dependency rule can be reviewed statically.

### HIGH-02: Guest Context assumptions exceed current evidence

**Problem:** the design assumes that a host-backed Context can safely expose guest package, paths, resources, services, and receivers.

**Impact:** one leaked host Context can expose host paths, host package identity, host resources, or host permissions.

**Modules:** Application, Resources, Storage, System Bridge.

**Recommendation:** treat Guest Context as a capability object with an explicit supported API subset, not as a drop-in `Context` until tests prove each method.

**Experiment:** required.

### HIGH-03: Package metadata completeness is not guaranteed by archive parsing

**Problem:** `PackageInfo` parsed from an archive is useful metadata, but does not recreate PMS settings, grants, visibility, resolver indexes, or install-time derived state.

**Impact:** Registry data may be mistaken for a system package record.

**Modules:** Package Registry, Resolver, Permission, System Bridge.

**Recommendation:** version the domain model and mark fields as archive-observed, host-derived, or unsupported.

**Experiment:** required for split APKs, signing, filters, providers, and API versions.

### HIGH-04: Shared APK/Dex/Resources are confused with shared runtime state

**Problem:** immutable code/resources may be shareable, but class statics, Application objects, ThreadLocals, native globals, and caches are process-scoped.

**Impact:** multi-instance isolation can fail even when filesystem paths are separate.

**Modules:** Multi-instance, Process, Code, Native, Resources.

**Recommendation:** define sharing only for immutable bytes first; require a process decision before claiming concurrent instance isolation.

**Experiment:** required.

### MEDIUM-01: Hidden API risk is acknowledged but not tracked per dependency

**Problem:** resource/application/activity designs mention hidden APIs without a concrete method ledger.

**Impact:** implementation can silently drift into unsupported reflection.

**Recommendation:** every experiment must list public API, alternative design, and blocked status before hidden API discussion.

### MEDIUM-02: Compatibility tiers lack measurable exit tests

**Problem:** tiers describe app categories but not exact pass/fail behavior.

**Impact:** “support” becomes anecdotal.

**Recommendation:** map each tier to the GuestTestApp phases and a versioned test matrix.

### MEDIUM-03: Storage and Package Registry transaction boundary is incomplete

**Problem:** the design names atomic import and recovery but not the ordering between APK, metadata, registry, and deletion.

**Impact:** orphaned packages or records can appear after interruption.

**Recommendation:** make registry publication a state machine and test interruption before runtime work.

### LOW-01: Process Slot Pool is premature

**Problem:** a slot pool was listed before measurements.

**Impact:** unnecessary scheduler complexity and hidden shared-state assumptions.

**Recommendation:** remove it from early milestones; revisit only after per-process cost data.

## Circular dependency audit

```text
Package Registry -> Identity -> Permission -> System Bridge
       |                                      ^
       v                                      |
Resolver -> Component Runtime -> Application -+
       \-> Activity Runtime ------------------+
```

The dangerous loop is `System Bridge -> Activity Runtime -> System Bridge`. Break it by making bridges policy/identity based and keeping Activity orchestration above them. A bridge may report “unsupported”; it must not invoke Activity to manufacture a result.

## Overall recommendation

Do not implement a broad Runtime. Execute only EXP-001 after review approval. If it fails, revise Code Runtime before any Resources/Application/Activity work.
