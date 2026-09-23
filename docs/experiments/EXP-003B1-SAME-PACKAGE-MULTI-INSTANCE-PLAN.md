# EXP-003B1 Same-package Multi-instance Plan

## Status

DESIGN ONLY. Not executed in task-14.

## Question

For one Guest package with Instance A and Instance B in the same Host process,
which Application, LoadedApk-adjacent, Resources, class-loader, and data
states are shared or conflicting?

## Forms

1. One shared DexClassLoader with two Controlled Contexts.
2. Two independent DexClassLoader instances with two Controlled Contexts.

## Observations

- Guest Application static constructor count.
- Independent or shared Application objects.
- Resources object identity and AssetManager identity.
- `applicationContext` target identity.
- Instance A/B `dataDir`, `filesDir`, cache and code-cache paths.
- Guest static state and class identity.
- PackageManager/application-context behavior.
- Host/System resource pollution.

## Process relation

Same-process execution does not create a Guest UID or framework package
identity. A separate process can reduce crash/native/static-state blast radius,
but does not automatically create a Guest LoadedApk. See
`docs/research/virtualization/13_PROCESS_MODELS.md` when available.

## Exit criteria

The experiment must identify whether same-package cache behavior merges
Resources, class loaders, Applications, or callbacks; prove data directories
remain distinct; and define whether same-process multi-instance is supported,
limited, or rejected. No conclusion may be based only on getter values.
