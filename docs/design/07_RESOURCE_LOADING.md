# Resource Runtime Design

## Model

Android resources are resolved through `Resources`, `ResourcesImpl`, `AssetManager`/`ApkAssets`, configuration, and display metrics. `ResourcesManager` owns process-level resource object coordination. A guest must have a resource view whose APK asset path and configuration are guest-specific.

## Options

| Option | Benefits | Costs |
|---|---|---|
| A: reuse host Resources | simple | incorrect IDs, themes, locale, density, and cache pollution |
| B: create guest Resources from APK assets | correct conceptual boundary | API/version and lifecycle details need experiments |
| C: rewrite resource IDs into host resources | possible host integration | high complexity and collision risk |

`PROPOSED`: option B. Keep host and guest `Resources` separate. Guest `Context` returns guest resources; host UI continues using host resources. Configuration changes create or update the guest resource view through a controlled owner.

Must test: `getResources`, `getAssets`, compiled `R` IDs, layouts, styles/themes, locale, density, night mode, split/resource overlays, and resource cache invalidation.

`RESEARCH NEEDED`: public API sufficiency on all target releases; `ApkAssets` and `ResourcesImpl` details vary and some useful constructors are hidden.
