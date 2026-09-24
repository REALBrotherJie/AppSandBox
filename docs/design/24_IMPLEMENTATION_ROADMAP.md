# Implementation Roadmap

> **Revision 2026-09-23:** execution is now gate-driven. No milestone may skip `Research -> Experiment -> ADR -> Implementation -> Integration`.

## M0 Research baseline

Goal: freeze clean-room rules, evidence format, target releases, and threat model.

Dependencies: current Phase 1. Deliverables: design docs, ADR template, experiment template. Experiments: archive parsing and storage failure cases. Exit: reviewers agree on status labels and non-goals. Risks: assuming undocumented behavior.

## M0.5 Assumption review

Goal: review the architecture and rank falsifiable assumptions before runtime code.

Dependencies: M0. Deliverables: `docs/review/`, assumption DAG, decision gates, EXP-001/002/003 plans. Experiments: none in this milestone. Exit: A1-A17 have owners, status, and tests. Risks: treating proposals as facts.

## M1 Package and storage foundation

Goal: immutable package revisions and isolated instances without runtime launch.

Dependencies: M0. Deliverables: schema, migration, digest, atomic import, registry recovery, path tests. Experiments: malformed APKs, duplicate import, crash during publish. Exit: registry and files reconcile deterministically. Risks: split APK/signing complexity.

M1 implementation is blocked until the review confirms that package/storage work does not depend on a successful runtime.

## M2 Resolver

Goal: resolve guest explicit/implicit intents without starting components.

Dependencies: M1. Deliverables: normalized component/filter model, deterministic resolver, trace output. Experiments: actions/categories/MIME/data/priority/exported/permission. Exit: resolution independent of PMS. Risks: Android matching edge cases.

## M3 Code and resource loading

M3 must begin with EXP-001 and EXP-002. Production code is not authorized by the roadmap until the corresponding decision gates pass.

Task-15 V-1: writable GuestStore APK fails on API36/target36. BLOCKER for
API34+ support; proposed ADR-0011 covers read-only immutable publication.
Production import permission changes are deferred to the next task.

Goal: load a test guest's classes/resources in a controlled process.

Dependencies: M1, M2 only for metadata. Deliverables: loader/resource owner, leak tests, failure cleanup. Experiments: Java, AndroidX, reflection, resources, configuration, JNI. Exit: no host class/resource leakage in defined tests. Risks: hidden implementation dependencies.

## M4 Application lifecycle

Status (task-15, 2026-09-24): C0 baseline preserved; C1 matrix, Guest layout,
minimal onCreate and limited multi-instance tests passed on API31/API36.
ADR-0009/0010 are PROPOSED, not production authorization. Hidden observation
is not currently needed; lifecycle dispatch and component hosting remain open.

Goal: construct and stop a guest Application with an explicit Context
contract, beginning with EXP-003A, then EXP-003B, then EXP-003C.

Dependencies: M3, Permission, Identity. Deliverables: Android application
model, Controlled Context contract, leakage matrix, compatibility levels, and
gate-specific plans. Experiments: attach/onCreate/process death. Exit:
idempotent lifecycle and cleanup. No production runtime or Guest Application
code is authorized until the gates pass.

## M5 Process boundary

Goal: move guest execution out of the host process.

Dependencies: M3/M4. Deliverables: narrow IPC protocol, crash/watchdog behavior. Experiments: concurrent instances and Binder lifetime. Exit: host remains responsive after guest crash. Risks: still shared UID/domain.

## M6 Activity subset

Goal: validate one launch strategy for a constrained Activity subset.

Dependencies: M2-M5. Deliverables: ADR after experiments, explicit unsupported list. Experiments: task/back/result/configuration/process death across releases. Exit: reproducible lifecycle tests.

## M7 Services/receivers/providers

Goal: add one component family at a time with policy matrices.

Dependencies: M6 and component-specific research. Exit: lifecycle, identity, permission, teardown tests.

## M8 System compatibility

Goal: add service families by support level, deny by default.

Dependencies: component runtime and permission model. Exit: per-API contract tests and version matrix.

## M9 Multi-instance and native

Goal: safe sharing of immutable artifacts and native ABI policy.

Dependencies: process and storage foundations. Exit: concurrent isolation and native collision tests.

## M10 Hardening and release

Goal: security, performance, compatibility, migration, and product review.

Dependencies: all supported features. Exit: documented support matrix and no unsupported claims.
