# ClassLoader Architecture Review

## Core facts

`BaseDexClassLoader` and its subclasses load dex/apk paths. `Class` objects retain the defining loader. Parent delegation and Android's boot/platform classes affect identity. A guest class with the same binary name as a host class is not interchangeable: casts, static state, resources, and reflection depend on the defining loader.

## Collision table

| Guest content | Host collision | Risk |
|---|---|---|
| `android.*` | framework classes | guest must not replace platform classes |
| `androidx.*` | host dependency | duplicate types, incompatible singleton/static state |
| `kotlin.*` | host Kotlin runtime | ABI/version mismatch |
| `okhttp.*`/`gson.*` | host libraries | class identity and hidden callbacks |
| custom SDK | host SDK | package-private and reflection assumptions |
| JNI-bound class | host same name | `FindClass`/loader context ambiguity |

## Candidate policies

- Parent-first: protects framework classes but leaks host dependencies.
- Child/guest-first: improves guest dependency ownership but risks shadowing framework-adjacent types and duplicate AndroidX.
- Explicit filtered delegation: framework/approved bridge packages delegate upward; guest-owned packages resolve from guest; all other host access is denied or declared.

`PROPOSED`: filtered delegation with a narrow bridge. Framework classes must come from the boot/platform loader. Guest must not define `android.*`; this should be enforced by policy and tests, not assumed solely from hierarchy.

## JNI

`System.loadLibrary` selects native code through loader/native search configuration. Native registration and `FindClass` are sensitive to the calling class loader and thread context. Two instances in one process can still share native globals and process-level linker state.

## Risks

Host context class loader leakage, reflection, ServiceLoader-like discovery, duplicate resources, static singletons, JNI class lookup, WebView/RenderScript assumptions, and dependency versions.

## Gate

No production loader until Level 1 experiments cover pure Java, duplicate dependency names, reflection, thread context loader, and a documented failure policy.
