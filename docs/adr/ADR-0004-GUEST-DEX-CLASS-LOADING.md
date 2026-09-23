# ADR-0004: Guest DEX Class Loading Baseline

Status: Accepted

## Context

EXP-001 tested whether an APK that is not installed in Android PackageManager can be loaded from an AppSandbox private path using an independent Android class loader.

## Observed evidence

On Xiaomi Mi 10, Android 12/API 31, the Guest APK remained absent from `pm path`. The APK was imported to:

```text
/data/user/0/com.example.appsandbox/files/guests/dc0fb5fe-272d-45fb-b798-05439a951ed7/base.apk
```

The Host class loader returned `ClassNotFoundException` for `GuestProbe`. A `DexClassLoader` loaded `GuestProbe`, whose defining loader was that loader. Reflection constructed the object, resolved `GuestHelper`, and returned the unique Guest marker. Two loaders for the same APK produced unequal `Class` objects and `isAssignableFrom == false`.

## Decision

AppSandbox may use an independent `DexClassLoader` as the Level 1 code-loading experiment baseline for ordinary guest dex classes from a private APK path.

## Consequences

Class identity includes the defining ClassLoader. Objects/classes from separate guest loaders must not be treated as interchangeable. Host and guest class paths remain separate, while framework classes are delegated to the platform/parent loader.

## Limitations

This ADR does not confirm Resources, Context, Application, Activity, Service, Provider, Binder, PackageManager virtualization, native/JNI loading, system-service compatibility, or multi-instance runtime semantics.

## References

- `docs/experiments/EXP-001-PLAN.md`
- `docs/experiments/EXP-001-RESULT.md`
- Android public `dalvik.system.DexClassLoader` API
