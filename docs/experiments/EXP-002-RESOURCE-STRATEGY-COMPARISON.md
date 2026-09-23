# EXP-002 Resource Strategy Comparison

Test device: Xiaomi Mi 10, Android 12, API 31. Guest package was not
installed in PackageManager.

| Dimension | Legacy Host-based | Option A: System-base + ResourcesLoader | Option B: archive ApplicationInfo |
|---|---|---|---|
| Base | Host `activity.resources.assets` | `Resources.getSystem().assets` | PackageManager-created resources |
| Host resource isolation | Failed | Passed | Passed |
| Guest resource isolation | Failed | Passed | Passed |
| Guest asset isolation | Failed | Passed | Passed |
| Host sees Guest asset | Yes after legacy loader | No in fresh A path | No |
| Layout XML | Failed | Passed | Passed |
| Configuration | Passed | Passed | Basic default read passed |
| `getIdentifier()` | 0 | Guest ID returned | Guest ID returned |
| API level | Existing but unsafe | API 30+ | Public API; candidate for API 28/29 |
| API classification | Public deprecated constructor plus shared Host base | Public deprecated `Resources` constructor plus public loader APIs | Public PackageManager API |
| Lifecycle | Shared AssetManager contamination risk | Experimental provider cleanup implemented | PackageManager-owned resource lifecycle |
| Production suitability | Rejected | Proposed API 30+ baseline | Proposed secondary/cross-version candidate |

## Current Interpretation

The legacy result was invalid as an isolation result because its base
`AssetManager` was the Host AssetManager. It also caused Guest-only asset
visibility through the Host AssetManager.

Option A produced an isolated Guest resource space on API 31, including
compiled layout XML. It remains experimental because the public
`Resources(AssetManager, DisplayMetrics, Configuration)` constructor is
deprecated.

Option B successfully created resources from a copied archive
`ApplicationInfo` with `PackageManager.getResourcesForApplication()`. The
tested API 31 path resolved Guest string, raw, asset, layout, metadata, and
isolation checks. API 28/29 remain unverified.

## Proposed Choice

```text
Preferred candidate = PROPOSED Option B
Secondary candidate = PROPOSED Option A for API 30+
```

Option B is preferred for further review because it avoids the deprecated
`Resources` constructor and returned a framework-created resource environment.
Neither choice is a production architecture decision yet.
