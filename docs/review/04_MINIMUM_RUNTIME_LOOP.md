# Minimum Runtime Loop

## Level 0: APK imported

Goal: copy and parse an APK. Required: current Phase 1 code. Not required: code/resources/runtime. Success: stable private path and metadata. Risk: malformed/archive edge cases.

## Level 1: Guest class loaded

Goal: load one ordinary guest Java/Kotlin class and invoke a pure method. Required: APK path, `BaseDexClassLoader`/`DexClassLoader`, reflection. Not required: Application, Activity, Resources, Binder, PackageManager. Success: class loader is guest-associated and method returns expected value. Risk: duplicate dependencies/class-loader leakage.

## Level 2: Guest resource loaded

Goal: read a string/drawable/layout from an uninstalled APK. Required: archive metadata and guest resource owner. Not required: Application/Activity. Success: values and resource IDs match the guest APK. Risk: hidden resource construction and cache/configuration behavior.

## Level 3: Application instantiated

Goal: construct the declared Application object. Required: Level 1 and a controlled context construction path. Not required: `onCreate` success or system services. Success: object type is correct and construction is bounded. Risk: constructor/static initialization.

## Level 4: Application.onCreate succeeds

Goal: run a controlled test Application lifecycle. Required: Levels 1-3, storage, resources, lifecycle owner. Not required: Activity/task/Binder parity. Success: onCreate completes and teardown is deterministic. Risk: common libraries assume installed-app services.

## Level 5: Ordinary guest logic

Goal: `getPackageName`, `getFilesDir`, and `getResources` return guest-scoped values. Required: guest context contract and storage/resources. Success: no host path/package leakage in defined calls. Risk: unsupported Context methods.

## Level 6: Guest UI as a View

Goal: render a guest-created View/layout inside a host Activity. Required: Levels 1-5 and resource/theme mapping. Not required: guest Activity token/task. Success: visible UI and event handling. Risk: theme/window assumptions.

## Level 7: Real Guest Activity runtime

Goal: preserve meaningful Activity lifecycle, task/window/result/configuration behavior. Required: all lower levels, process model, and a validated system cooperation strategy. Success: explicit test matrix. Risk: platform-owned state cannot be recreated by user code.

## Minimum claim

The first credible “guest code runs” claim is Level 1, not Activity launch. Each level is an independent gate and must have a recorded result.
