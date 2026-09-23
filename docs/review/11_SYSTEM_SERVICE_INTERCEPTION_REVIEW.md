# System Service Interception Review

## Least-invasive order

1. Public Android API with no adaptation.
2. Guest `Context` wrapper for path/package/capability changes.
3. Guest-owned PackageManager/resolver model.
4. Runtime-owned service implementation for purely logical behavior.
5. Intent translation at a defined host boundary.
6. Public Binder manager path with typed argument/return adaptation.
7. Hidden API only as a documented blocked dependency, never as an automatic bypass.
8. Unsupported capability.

## Classification examples

| Problem | First candidate | Why |
|---|---|---|
| guest package query | custom PackageManager facade | logical registry is local |
| guest files path | Context/storage mapping | no system identity required |
| guest-to-guest intent | runtime resolver | PMS cannot see guest |
| notification attribution | host API plus policy | system validates host identity |
| Activity task | host component strategy | ATMS owns task state |
| AppOps/UID | host capability or unsupported | OS identity is not local |

The rule is not “never use Binder”; it is “do not intercept Binder until a public/local boundary is proven insufficient and the exact contract is understood.” No Binder hook is justified by the current evidence.
