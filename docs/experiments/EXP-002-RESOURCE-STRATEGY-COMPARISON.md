# EXP-002 Resource Strategy Comparison

## Current decision

Legacy and Option A are rejected. They may remain as historical diagnostics,
but neither may run in the normal runner or be used for Guest runtime
construction. Option B is the only formal candidate.

Test device: Xiaomi Mi 10, Android 12, API 31. Guest package was not
installed in PackageManager.

| Dimension | Legacy Host-based (REJECTED) | Option A: System-base + ResourcesLoader (REJECTED) | Option B: archive ApplicationInfo (ONLY FORMAL CANDIDATE) |
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

Option A's historical reads are not an isolation result. Its base is
`Resources.getSystem().assets`, and `Resources.addLoaders()` mutates the shared
System AssetManager loader set. It is rejected as shared AssetManager
mutation.

Option B successfully created resources from a copied archive
`ApplicationInfo` with `PackageManager.getResourcesForApplication()`. The
tested API 31 path resolved Guest string, raw, asset, layout, metadata, and
isolation checks. API 28/29 remain unverified.

## Proposed Choice

```text
Preferred candidate = PROPOSED Option B
Option A = REJECTED
```

Option B is the only formal candidate because it avoids the deprecated
constructor and uses the PackageManager resource API. This does not claim a
security boundary or all-version compatibility.
