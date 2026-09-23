# Resources Architecture Review

## Scope separation

The minimum resource experiment only proves archive resource reading. It does not prove configuration propagation, themes, Activity resources, or cache isolation.

## Dependencies

Guest resources depend on an APK asset path, resource IDs/table, `AssetManager`/`ApkAssets` behavior, `Resources`/`ResourcesImpl`, `Configuration`, `DisplayMetrics`, and lifecycle owners. `ResourcesManager` may cache process-level objects, so resource identity and invalidation need observation.

## Required propagation

| Change | Minimum read test | Full runtime requirement |
|---|---|---|
| locale | fixed configuration | update guest resources |
| night mode | fixed configuration | theme recreation/update |
| orientation | fixed metrics | layout/configuration callback |
| density | fixed metrics | display/resource selection |
| display | not needed | window/display mapping |
| theme | load style | Activity/Application theme ownership |
| cache | one read | no cross-guest stale values |

`PROPOSED`: maintain a guest-owned resource view and explicit configuration snapshots. Do not return host Resources from guest Context. `RESEARCH NEEDED`: public-only construction and cache behavior on API 28-36.

## Gate

EXP-002 must pass string, drawable, and layout reads before Application or UI work. Configuration propagation is a later gate.
