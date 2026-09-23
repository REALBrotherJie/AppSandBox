# EXP-003A Controlled Context Plan

## Question

Can a limited Guest-facing Context expose correct package, loader, resources,
assets, ApplicationInfo, and per-instance file paths without returning Host
semantics?

## Hypothesis

A ContextWrapper-based experimental Context can provide C0 capabilities if all
identity, resource, and storage getters are explicitly overridden.

## Dependencies

EXP-001 Guest ClassLoader, EXP-002 Option B resources, archive
ApplicationInfo, ADR-0003 instance storage model, and ADR-0002 identity rules.

## Tests

Verify package name, Guest loader, resource marker, asset marker, logical
ApplicationInfo, files/cache/code-cache/data/no-backup paths, path ownership,
Host path inequality, and Guest/Host file visibility in both directions.

## Negative Controls

Host loader cannot load Guest probe; Guest resources cannot read Host-only
marker; Guest assets cannot read Host-only asset; Guest files path differs
from Host path; `getApplicationContext()` cannot return Host Application.

## Success

All C0 values are Guest-correct, storage is instance-scoped, and no Host
private marker/path leaks.

## Failure

Any unintentional Host path, package, loader, resources, assets, or Application
leak stops B and C. No hidden API or hook is introduced to force success.

